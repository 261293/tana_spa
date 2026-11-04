import { createClient } from "npm:@supabase/supabase-js@2.118.0";
import { accessToken, type PushMessage, send, type ServiceAccount, tokenHint } from "./fcm.ts";

export type PushDb = {
  tokensOf(userId: string): Promise<string[]>;
  deleteTokens(tokens: string[]): Promise<void>;
  markPushed(notificationId: string): Promise<void>;
};

export type Deps = { env: (key: string) => string | undefined; db: PushDb; fetchFn: typeof fetch };

type NotificationRecord = { id: string; user_id: string; type: string; title: string; body: string | null; data: Record<string, string> };

const encoder = new TextEncoder();

function sameSecret(sent: string | null, expected: string | undefined): boolean {
  if (!sent || !expected) return false;
  const a = encoder.encode(sent);
  const b = encoder.encode(expected);
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
  return difference === 0;
}

function serviceAccount(env: Deps["env"]): { account: ServiceAccount; projectId: string } | null {
  const raw = env("FCM_SERVICE_ACCOUNT_JSON");
  if (!raw?.trim()) return null;
  let account: ServiceAccount;
  try {
    account = JSON.parse(raw);
  } catch {
    throw new Error("FCM service account is not readable JSON");
  }
  const projectId = env("FCM_PROJECT_ID")?.trim() || account.project_id;
  if (!projectId) throw new Error("FCM service account has no project_id; set FCM_PROJECT_ID");
  if (!account.client_email) throw new Error("FCM service account has no client_email");
  if (!account.private_key) throw new Error("FCM service account has no private_key");
  return { account, projectId };
}

function pushMessage(record: NotificationRecord): PushMessage {
  return { title: record.title, body: record.body, data: { ...record.data, type: record.type } };
}

export async function handle(req: Request, deps: Deps): Promise<Response> {
  if (!sameSecret(req.headers.get("x-push-secret"), deps.env("PUSH_WEBHOOK_SECRET"))) {
    return new Response("unauthorized", { status: 401 });
  }
  const record: NotificationRecord | undefined = (await req.json().catch(() => null))?.record;
  if (!record?.id || !record.user_id) return new Response("no record", { status: 400 });

  const tokens = await deps.db.tokensOf(record.user_id);
  const message = pushMessage(record);
  const fcm = serviceAccount(deps.env);
  if (!fcm) {
    console.warn("FCM is not configured: using the DEV push sender. The notification is stored, but nothing reaches a device.");
    for (const token of tokens) {
      console.info(`DEV push to ${tokenHint(token)} title='${message.title}' dataKeys=${Object.keys(message.data)}`);
    }
    return Response.json({ devices: tokens.length, delivered: 0, removed: 0 });
  }

  let outcomes: string[] = [];
  if (tokens.length > 0) {
    try {
      const bearer = await accessToken(fcm.account, deps.fetchFn);
      outcomes = await Promise.all(tokens.map((token) => send(token, message, fcm.projectId, bearer, deps.fetchFn)));
    } catch (e) {
      console.warn(`FCM authentication failed: ${(e as Error).message}`);
    }
  }
  const dead = tokens.filter((_, i) => outcomes[i] === "unregistered");
  const delivered = outcomes.filter((outcome) => outcome === "delivered").length;
  if (dead.length > 0) await deps.db.deleteTokens(dead);
  if (delivered > 0) await deps.db.markPushed(record.id);

  console.info(
    `notification ${record.id} user=${record.user_id} type=${record.type} devices=${tokens.length} delivered=${delivered} removed=${dead.length} via fcm_http_v1`,
  );
  return Response.json({ devices: tokens.length, delivered, removed: dead.length });
}

function supabaseDb(): PushDb {
  const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  return {
    async tokensOf(userId) {
      const { data, error } = await client.from("device_tokens").select("token").eq("user_id", userId);
      if (error) throw new Error(`device_tokens read failed: ${error.code}`);
      return data.map((row) => row.token);
    },
    async deleteTokens(tokens) {
      const { error } = await client.from("device_tokens").delete().in("token", tokens);
      if (error) throw new Error(`device_tokens delete failed: ${error.code}`);
    },
    async markPushed(notificationId) {
      const { error } = await client.from("notifications").update({ sent_push_at: new Date().toISOString() }).eq("id", notificationId);
      if (error) throw new Error(`notifications stamp failed: ${error.code}`);
    },
  };
}

if (import.meta.main) {
  const db = supabaseDb();
  Deno.serve((req) => handle(req, { env: (key) => Deno.env.get(key), db, fetchFn: fetch }));
}
