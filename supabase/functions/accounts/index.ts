import { createClient } from "npm:@supabase/supabase-js@2.118.0";
import { canCreate, hasManageAccounts } from "./rules.ts";

export type CreatedUser = { id: string } | "email_taken" | "refused";

export type Backend = {
  callerId(jwt: string): Promise<string | null>;
  activeRoleOf(userId: string): Promise<string | null>;
  createUser(email: string, password: string, fullName: string): Promise<CreatedUser>;
  setRole(userId: string, role: string): Promise<void>;
  deleteUser(userId: string): Promise<void>;
};

type CreateAccountRequest = { displayName: string; email: string; password: string; role: string };

const MIN_PASSWORD_LENGTH = 8;
const MAX_PASSWORD_LENGTH = 128;
const MAX_EMAIL_LENGTH = 254;
const MAX_NAME_LENGTH = 100;
const EMAIL = /^[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}$/;

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, apikey, content-type, accept-language, x-client-info, x-request-id, x-session-id",
  "access-control-allow-methods": "POST, OPTIONS",
};

function apiError(status: number, code: string, message: string, field?: string): Response {
  return Response.json(field ? { code, message, field } : { code, message }, { status, headers: CORS });
}

function bearerToken(req: Request): string | null {
  return req.headers.get("authorization")?.match(/^Bearer\s+(\S+)$/i)?.[1] ?? null;
}

async function readRequest(req: Request): Promise<CreateAccountRequest | null> {
  const body = await req.json().catch(() => null);
  if (typeof body !== "object" || body === null || Array.isArray(body)) return null;
  const { displayName, email, password, role } = body;
  const fields = [displayName, email, password, role];
  return fields.every((field) => typeof field === "string") ? { displayName, email, password, role } : null;
}

function validDetails({ displayName, email, password }: CreateAccountRequest): boolean {
  const name = displayName.trim();
  const address = email.trim();
  return name.length > 0 && name.length <= MAX_NAME_LENGTH &&
    address.length <= MAX_EMAIL_LENGTH && EMAIL.test(address) &&
    password.length >= MIN_PASSWORD_LENGTH && password.length <= MAX_PASSWORD_LENGTH;
}

async function createAccount(req: Request, backend: Backend): Promise<Response> {
  const jwt = bearerToken(req);
  const callerId = jwt ? await backend.callerId(jwt) : null;
  const callerRole = callerId ? await backend.activeRoleOf(callerId) : null;
  if (!callerId || !callerRole) return apiError(401, "unauthorized", "Authentication required");
  if (!hasManageAccounts(callerRole)) {
    console.warn(`Refused accounts for user ${callerId}: role ${callerRole} lacks MANAGE_ACCOUNTS`);
    return apiError(403, "forbidden", "This account cannot create accounts");
  }

  const request = await readRequest(req);
  if (!request) return apiError(400, "invalid_request", "Malformed request body");
  if (!canCreate(callerRole, request.role)) {
    console.warn(`Refused: ${callerRole} ${callerId} tried to create a role it cannot create`);
    return apiError(403, "role_not_allowed", `This account cannot create a ${request.role}`, "role");
  }
  if (!validDetails(request)) return apiError(400, "invalid_request", "Those details are not valid");

  const displayName = request.displayName.trim();
  const email = request.email.trim().toLowerCase();
  const created = await backend.createUser(email, request.password, displayName);
  if (created === "email_taken") return apiError(409, "email_taken", "That address already has an account", "email");
  if (created === "refused") return apiError(400, "invalid_request", "Those details are not valid");

  try {
    await backend.setRole(created.id, request.role);
  } catch (e) {
    // The trigger made this a customer; leaving it would let the new person sign in with the wrong role.
    console.error(`Role ${request.role} could not be set on new account ${created.id} (${(e as Error).message}); removing it`);
    await backend.deleteUser(created.id).catch((cleanup) =>
      console.error(`Could not remove half-created account ${created.id}: ${(cleanup as Error).message}`)
    );
    return apiError(500, "internal_error", "Something went wrong");
  }

  console.info(`${callerRole} ${callerId} created account ${created.id} (${request.role})`);
  return Response.json({ id: created.id, displayName, email, role: request.role }, { status: 201, headers: CORS });
}

export async function handle(req: Request, backend: Backend): Promise<Response> {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  try {
    return await createAccount(req, backend);
  } catch (e) {
    console.error(`Account creation failed: ${(e as Error).message}`);
    return apiError(500, "internal_error", "Something went wrong");
  }
}

function supabaseBackend(): Backend {
  const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  return {
    async callerId(jwt) {
      const { data, error } = await client.auth.getUser(jwt);
      if (!error) return data.user.id;
      if (error.status && error.status < 500) return null;
      throw new Error(`token check failed: ${error.code ?? error.status}`);
    },
    async activeRoleOf(userId) {
      const { data, error } = await client.from("users").select("role").eq("id", userId).is("deleted_at", null)
        .eq("status", "active").maybeSingle();
      if (error) throw new Error(`users read failed: ${error.code}`);
      return data?.role ?? null;
    },
    async createUser(email, password, fullName) {
      const { data, error } = await client.auth.admin.createUser({
        email,
        password,
        email_confirm: true,
        user_metadata: { full_name: fullName },
      });
      if (!error) return { id: data.user.id };
      if (error.code === "email_exists") return "email_taken";
      if (error.status === 400 || error.status === 422) {
        console.warn(`Auth refused the new account: ${error.code ?? error.status}`);
        return "refused";
      }
      throw new Error(`auth createUser failed: ${error.code ?? error.status}`);
    },
    async setRole(userId, role) {
      const { data, error } = await client.from("users").update({ role }).eq("id", userId).select("id");
      if (error) throw new Error(`users update failed: ${error.code}`);
      if (data.length !== 1) throw new Error(`users update matched ${data.length} rows`);
    },
    async deleteUser(userId) {
      const { error } = await client.auth.admin.deleteUser(userId);
      if (error) throw new Error(`auth deleteUser failed: ${error.code ?? error.status}`);
    },
  };
}

if (import.meta.main) {
  const backend = supabaseBackend();
  Deno.serve((req) => handle(req, backend));
}
