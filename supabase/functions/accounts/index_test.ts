import { assertEquals } from "jsr:@std/assert@1.0.13";
import { createClient } from "npm:@supabase/supabase-js@2.118.0";
import { type Backend, handle } from "./index.ts";

const CALLER_ID = "7a1c0d3e-0000-4000-8000-000000000001";
const NEW_ID = "7a1c0d3e-0000-4000-8000-000000000002";
const PASSWORD = "correct-horse-battery";

function post(body: unknown, token: string | null = "caller-jwt") {
  const headers = new Headers({ "content-type": "application/json" });
  if (token !== null) headers.set("authorization", `Bearer ${token}`);
  return new Request("http://localhost/accounts", {
    method: "POST",
    headers,
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

function account(overrides: Record<string, unknown> = {}) {
  return { displayName: "Lan Tran", email: "lan@example.com", password: PASSWORD, role: "staff", ...overrides };
}

function fakeBackend(callerRole: string | null, overrides: Partial<Backend> = {}) {
  const calls = { created: [] as string[][], roles: [] as string[][], deleted: [] as string[] };
  const backend: Backend = {
    callerId: (jwt) => Promise.resolve(jwt === "caller-jwt" ? CALLER_ID : null),
    activeRoleOf: () => Promise.resolve(callerRole),
    createUser: (email, password, fullName) => {
      calls.created.push([email, password, fullName]);
      return Promise.resolve({ id: NEW_ID });
    },
    setRole: (id, role) => {
      calls.roles.push([id, role]);
      return Promise.resolve();
    },
    deleteUser: (id) => {
      calls.deleted.push(id);
      return Promise.resolve();
    },
    ...overrides,
  };
  return { backend, calls };
}

Deno.test("creates_with_normalized_details", async () => {
  const { backend, calls } = fakeBackend("owner");

  const response = await handle(post(account({ displayName: "  Lan Tran ", email: " Lan@Example.COM " })), backend);

  assertEquals(response.status, 201);
  assertEquals(await response.json(), { id: NEW_ID, displayName: "Lan Tran", email: "lan@example.com", role: "staff" });
  assertEquals(calls.created, [["lan@example.com", PASSWORD, "Lan Tran"]]);
  assertEquals(calls.roles, [[NEW_ID, "staff"]]);
});

Deno.test("role_failure_removes_the_new_auth_user", async () => {
  const { backend, calls } = fakeBackend("admin", { setRole: () => Promise.reject(new Error("users update failed: 42501")) });

  const response = await handle(post(account({ role: "owner" })), backend);

  assertEquals(response.status, 500);
  assertEquals(await response.json(), { code: "internal_error", message: "Something went wrong" });
  assertEquals(calls.deleted, [NEW_ID]);
});

Deno.test("missing_or_rejected_token_is_401", async () => {
  for (const request of [post(account(), null), post(account(), "forged-jwt")]) {
    const { backend, calls } = fakeBackend("admin");
    const response = await handle(request, backend);
    assertEquals(response.status, 401);
    assertEquals(await response.json(), { code: "unauthorized", message: "Authentication required" });
    assertEquals(calls.created, []);
  }
});

Deno.test("inactive_caller_is_401", async () => {
  const { backend } = fakeBackend(null);
  const response = await handle(post(account()), backend);
  assertEquals(response.status, 401);
  await response.body?.cancel();
});

Deno.test("permission_is_checked_before_the_body", async () => {
  for (const role of ["customer", "staff"]) {
    const { backend } = fakeBackend(role);
    const response = await handle(post("not json"), backend);
    assertEquals(response.status, 403);
    assertEquals(await response.json(), { code: "forbidden", message: "This account cannot create accounts" });
  }
});

Deno.test("malformed_body_is_400", async () => {
  for (const body of ["not json", "[]", JSON.stringify({ ...account(), password: 12345678 }), JSON.stringify({ email: "a@b.co" })]) {
    const { backend, calls } = fakeBackend("admin");
    const response = await handle(post(body), backend);
    assertEquals(response.status, 400);
    assertEquals(await response.json(), { code: "invalid_request", message: "Malformed request body" });
    assertEquals(calls.created, []);
  }
});

Deno.test("role_outside_the_callers_reach_is_role_not_allowed", async () => {
  for (const [caller, target] of [["owner", "admin"], ["owner", "owner"], ["admin", "superuser"], ["owner", "Staff"]]) {
    const { backend, calls } = fakeBackend(caller);
    const response = await handle(post(account({ role: target, password: "short" })), backend);
    assertEquals(response.status, 403);
    assertEquals(await response.json(), {
      code: "role_not_allowed",
      message: `This account cannot create a ${target}`,
      field: "role",
    });
    assertEquals(calls.created, []);
  }
});

Deno.test("invalid_details_never_reach_auth", async () => {
  const invalid = [
    { displayName: "   " },
    { displayName: "x".repeat(101) },
    { email: "" },
    { email: "not-an-email" },
    { email: "lan@example.c" },
    { email: `${"a".repeat(243)}@example.com` },
    { password: "" },
    { password: "seven77" },
    { password: "p".repeat(129) },
  ];
  for (const overrides of invalid) {
    const { backend, calls } = fakeBackend("admin");
    const response = await handle(post(account(overrides)), backend);
    assertEquals(response.status, 400, JSON.stringify(overrides));
    assertEquals(await response.json(), { code: "invalid_request", message: "Those details are not valid" });
    assertEquals(calls.created, []);
  }
});

Deno.test("limits_are_inclusive", async () => {
  const { backend, calls } = fakeBackend("admin");
  const response = await handle(
    post(account({ displayName: "x".repeat(100), email: `${"a".repeat(242)}@example.com`, password: "eight888" })),
    backend,
  );
  assertEquals(response.status, 201);
  await response.body?.cancel();
  assertEquals(calls.created.length, 1);
});

Deno.test("auth_outcomes_map_to_codes", async () => {
  const cases = [
    ["email_taken", 409, { code: "email_taken", message: "That address already has an account", field: "email" }],
    ["refused", 400, { code: "invalid_request", message: "Those details are not valid" }],
  ] as const;
  for (const [outcome, status, body] of cases) {
    const { backend, calls } = fakeBackend("admin", { createUser: () => Promise.resolve(outcome) });
    const response = await handle(post(account()), backend);
    assertEquals(response.status, status);
    assertEquals(await response.json(), body);
    assertEquals(calls.roles, []);
  }
});

Deno.test("backend_failure_is_500", async () => {
  const { backend } = fakeBackend("admin", { activeRoleOf: () => Promise.reject(new Error("users read failed: 08006")) });
  const response = await handle(post(account()), backend);
  assertEquals(response.status, 500);
  assertEquals(await response.json(), { code: "internal_error", message: "Something went wrong" });
});

Deno.test("preflight_is_answered", async () => {
  const { backend } = fakeBackend(null);
  const response = await handle(new Request("http://localhost/accounts", { method: "OPTIONS" }), backend);
  assertEquals(response.ok, true);
  assertEquals(response.headers.get("access-control-allow-origin"), "*");
  await response.body?.cancel();
});

async function localStack(): Promise<Record<string, string>> {
  const fromEnv = {
    API_URL: Deno.env.get("SUPABASE_URL"),
    ANON_KEY: Deno.env.get("SUPABASE_ANON_KEY"),
    SERVICE_ROLE_KEY: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"),
  };
  if (fromEnv.API_URL && fromEnv.ANON_KEY && fromEnv.SERVICE_ROLE_KEY) return fromEnv as Record<string, string>;
  const npx = Deno.build.os === "windows" ? "npx.cmd" : "npx";
  const { success, stdout } = await new Deno.Command(npx, { args: ["supabase", "status", "-o", "env"], stderr: "null" }).output();
  if (!success) throw new Error("the local stack is not running: npx supabase start");
  return Object.fromEntries(
    new TextDecoder().decode(stdout).split(/\r?\n/).map((line) => line.match(/^([A-Z_]+)="?([^"]*)"?$/)).filter((m) => m !== null)
      .map((m) => [m[1], m[2]]),
  );
}

Deno.test({
  name: "local_stack",
  sanitizeOps: false,
  sanitizeResources: false,
  fn: async (t) => {
    const stack = await localStack();
    const functionUrl = `${stack.API_URL}/functions/v1/accounts`;
    const options = { auth: { persistSession: false, autoRefreshToken: false } };
    const admin = createClient(stack.API_URL, stack.SERVICE_ROLE_KEY, options);
    const run = crypto.randomUUID().slice(0, 8);
    const created: string[] = [];

    async function signedIn(role: string): Promise<string> {
      const email = `accounts-${role}-${run}@example.com`;
      const { data, error } = await admin.auth.admin.createUser({ email, password: PASSWORD, email_confirm: true });
      if (error) throw error;
      created.push(data.user.id);
      if (role !== "customer") {
        const { error: roleError } = await admin.from("users").update({ role }).eq("id", data.user.id);
        if (roleError) throw roleError;
      }
      const session = await createClient(stack.API_URL, stack.ANON_KEY, options).auth.signInWithPassword({
        email,
        password: PASSWORD,
      });
      if (session.error) throw session.error;
      return session.data.session.access_token;
    }

    async function call(body: unknown, token: string | null) {
      const headers: Record<string, string> = { "content-type": "application/json", apikey: stack.ANON_KEY };
      if (token) headers.authorization = `Bearer ${token}`;
      const response = await fetch(functionUrl, { method: "POST", headers, body: JSON.stringify(body) });
      const text = await response.text();
      return { status: response.status, body: text ? JSON.parse(text) : null };
    }

    try {
      const owner = await signedIn("owner");
      const customer = await signedIn("customer");
      const staffEmail = `accounts-new-staff-${run}@example.com`;

      await t.step("owner_creates_staff", async () => {
        const { status, body } = await call(account({ displayName: " New Staff ", email: staffEmail.toUpperCase() }), owner);
        assertEquals(status, 201);
        created.push(body.id);
        assertEquals(body, { id: body.id, displayName: "New Staff", email: staffEmail, role: "staff" });
        const { data } = await admin.from("users").select("role, full_name, email").eq("id", body.id).single();
        assertEquals(data, { role: "staff", full_name: "New Staff", email: staffEmail });
        const session = await createClient(stack.API_URL, stack.ANON_KEY, options).auth.signInWithPassword({
          email: staffEmail,
          password: PASSWORD,
        });
        assertEquals(session.error, null);
      });

      await t.step("owner_cannot_create_admin", async () => {
        const { status, body } = await call(account({ role: "admin", email: `accounts-admin-${run}-x@example.com` }), owner);
        assertEquals(status, 403);
        assertEquals(body, { code: "role_not_allowed", message: "This account cannot create a admin", field: "role" });
      });

      await t.step("customer_is_forbidden", async () => {
        const { status, body } = await call(account({ email: `accounts-by-customer-${run}@example.com` }), customer);
        assertEquals(status, 403);
        assertEquals(body, { code: "forbidden", message: "This account cannot create accounts" });
      });

      await t.step("duplicate_email_is_409", async () => {
        const { status, body } = await call(account({ email: staffEmail }), owner);
        assertEquals(status, 409);
        assertEquals(body, { code: "email_taken", message: "That address already has an account", field: "email" });
      });

      await t.step("no_token_is_401", async () => {
        assertEquals((await call(account(), null)).status, 401);
        const anonAsBearer = await call(account(), stack.ANON_KEY);
        assertEquals(anonAsBearer, { status: 401, body: { code: "unauthorized", message: "Authentication required" } });
      });
    } finally {
      for (const id of created) await admin.auth.admin.deleteUser(id);
    }
  },
});
