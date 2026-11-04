import { assertEquals } from "jsr:@std/assert@1.0.13";
import { type Deps, handle } from "./index.ts";
import { testServiceAccount } from "./test_key.ts";

const SECRET = "throwaway-webhook-secret";
const NOTIFICATION_ID = "5d0f8f8e-0000-4000-8000-000000000001";
const USER_ID = "5d0f8f8e-0000-4000-8000-000000000002";

function webhook(secret: string | null) {
  const headers = new Headers({ "content-type": "application/json" });
  if (secret !== null) headers.set("x-push-secret", secret);
  return new Request("http://localhost/push", {
    method: "POST",
    headers,
    body: JSON.stringify({
      type: "INSERT",
      table: "notifications",
      record: { id: NOTIFICATION_ID, user_id: USER_ID, type: "promotion", title: "Hello", body: "Gift", data: { code: "FIRSTVISIT" } },
    }),
  });
}

function fakeDeps(env: Record<string, string>, tokens: string[]) {
  const calls = { loaded: [] as string[], deleted: [] as string[][], pushed: [] as string[], fetched: [] as string[] };
  const deps: Deps = {
    env: (key) => env[key],
    db: {
      tokensOf: (userId) => {
        calls.loaded.push(userId);
        return Promise.resolve(tokens);
      },
      deleteTokens: (dead) => {
        calls.deleted.push(dead);
        return Promise.resolve();
      },
      markPushed: (id) => {
        calls.pushed.push(id);
        return Promise.resolve();
      },
    },
    fetchFn: ((input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      calls.fetched.push(url);
      if (url.endsWith("/token")) return Promise.resolve(Response.json({ access_token: "ya29.test", expires_in: 3599 }));
      const token = JSON.parse(String(init?.body)).message.token;
      return Promise.resolve(
        token === "dead-device-token"
          ? Response.json({ error: { status: "UNREGISTERED" } }, { status: 404 })
          : new Response("{}", { status: 200 }),
      );
    }) as typeof fetch,
  };
  return { deps, calls };
}

Deno.test("rejects_wrong_secret", async () => {
  for (const [configured, sent] of [[SECRET, "wrong"], [SECRET, null], [undefined, null], [undefined, ""]] as const) {
    const env: Record<string, string> = configured ? { PUSH_WEBHOOK_SECRET: configured } : {};
    const { deps, calls } = fakeDeps(env, ["live-device-token"]);
    const response = await handle(webhook(sent), deps);
    assertEquals(response.status, 401);
    await response.body?.cancel();
    assertEquals(calls.loaded, []);
    assertEquals(calls.fetched, []);
  }
});

Deno.test("unregistered_token_is_deleted", async () => {
  const { account } = await testServiceAccount();
  const { deps, calls } = fakeDeps(
    { PUSH_WEBHOOK_SECRET: SECRET, FCM_SERVICE_ACCOUNT_JSON: JSON.stringify(account) },
    ["live-device-token", "dead-device-token"],
  );

  const response = await handle(webhook(SECRET), deps);

  assertEquals(response.status, 200);
  assertEquals(await response.json(), { devices: 2, delivered: 1, removed: 1 });
  assertEquals(calls.loaded, [USER_ID]);
  assertEquals(calls.deleted, [["dead-device-token"]]);
  assertEquals(calls.pushed, [NOTIFICATION_ID]);
});

Deno.test("nothing_delivered_leaves_sent_push_at_alone", async () => {
  const { account } = await testServiceAccount();
  const { deps, calls } = fakeDeps(
    { PUSH_WEBHOOK_SECRET: SECRET, FCM_SERVICE_ACCOUNT_JSON: JSON.stringify(account) },
    ["dead-device-token"],
  );

  const response = await handle(webhook(SECRET), deps);

  assertEquals(await response.json(), { devices: 1, delivered: 0, removed: 1 });
  assertEquals(calls.pushed, []);
});

Deno.test("no_service_account_only_logs", async () => {
  const { deps, calls } = fakeDeps({ PUSH_WEBHOOK_SECRET: SECRET }, ["live-device-token"]);

  const response = await handle(webhook(SECRET), deps);

  assertEquals(response.status, 200);
  await response.body?.cancel();
  assertEquals(calls.fetched, []);
  assertEquals(calls.deleted, []);
  assertEquals(calls.pushed, []);
});
