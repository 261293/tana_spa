import { assertEquals } from "jsr:@std/assert@1.0.13";
import { accessToken, send } from "./fcm.ts";
import { testServiceAccount } from "./test_key.ts";

function fromBase64Url(text: string): Uint8Array<ArrayBuffer> {
  const base64 = text.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(text.length / 4) * 4, "=");
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

Deno.test("jwt_has_firebase_scope", async () => {
  const { account, publicKey } = await testServiceAccount();
  let url = "";
  let form = new URLSearchParams();
  const fetchFn = ((input: string | URL | Request, init?: RequestInit) => {
    url = String(input);
    form = new URLSearchParams(String(init?.body));
    return Promise.resolve(Response.json({ access_token: "ya29.test", expires_in: 3599 }));
  }) as typeof fetch;

  assertEquals(await accessToken(account, fetchFn), "ya29.test");
  assertEquals(url, "https://token.test/token");
  assertEquals(form.get("grant_type"), "urn:ietf:params:oauth:grant-type:jwt-bearer");

  const [header, claims, signature] = form.get("assertion")!.split(".");
  assertEquals(JSON.parse(new TextDecoder().decode(fromBase64Url(header))), { alg: "RS256", typ: "JWT" });
  const payload = JSON.parse(new TextDecoder().decode(fromBase64Url(claims)));
  assertEquals(payload.scope, "https://www.googleapis.com/auth/firebase.messaging");
  assertEquals(payload.iss, account.client_email);
  assertEquals(payload.aud, account.token_uri);
  assertEquals(payload.exp - payload.iat, 3600);
  const verified = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    publicKey,
    fromBase64Url(signature),
    new TextEncoder().encode(`${header}.${claims}`),
  );
  assertEquals(verified, true);
});

Deno.test("send_builds_the_fcm_v1_message_and_classifies_the_answer", async () => {
  const requests: { url: string; auth: string | null; body: unknown }[] = [];
  const answers = [
    new Response("{}", { status: 200 }),
    Response.json({ error: { status: "UNREGISTERED" } }, { status: 400 }),
    new Response("", { status: 404 }),
    Response.json({ error: { status: "NOT_FOUND" } }, { status: 404 }),
    Response.json({ error: { status: "INVALID_ARGUMENT" } }, { status: 404 }),
    Response.json({ error: { status: "SENDER_ID_MISMATCH" } }, { status: 403 }),
    Response.json({ error: { status: "UNAVAILABLE" } }, { status: 503 }),
  ];
  const fetchFn = ((input: string | URL | Request, init?: RequestInit) => {
    requests.push({
      url: String(input),
      auth: new Headers(init?.headers).get("authorization"),
      body: JSON.parse(String(init?.body)),
    });
    return Promise.resolve(answers.shift()!);
  }) as typeof fetch;
  const message = { title: "Hello", body: null, data: { code: "FIRSTVISIT", type: "promotion" } };

  const outcomes = [];
  for (let i = 0; i < 7; i++) outcomes.push(await send(`device-token-${i}`, message, "tana-test", "ya29.test", fetchFn));

  assertEquals(outcomes, ["delivered", "unregistered", "unregistered", "unregistered", "failed", "unregistered", "failed"]);
  assertEquals(requests[0].url, "https://fcm.googleapis.com/v1/projects/tana-test/messages:send");
  assertEquals(requests[0].auth, "Bearer ya29.test");
  assertEquals(requests[0].body, {
    message: {
      token: "device-token-0",
      notification: { title: "Hello" },
      data: { code: "FIRSTVISIT", type: "promotion" },
      android: { priority: "HIGH", notification: { channel_id: "tana_default" } },
      apns: { headers: { "apns-priority": "10" }, payload: { aps: { sound: "default" } } },
    },
  });
});
