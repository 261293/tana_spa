export type ServiceAccount = {
  project_id?: string;
  client_email: string;
  private_key: string;
  token_uri?: string;
};

export type PushMessage = { title: string; body?: string | null; data: Record<string, string> };

export type PushOutcome = "delivered" | "unregistered" | "failed";

const MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
const ASSERTION_LIFETIME_SECONDS = 3600;
const GONE = new Set<string | undefined>(["UNREGISTERED", "SENDER_ID_MISMATCH"]);
const KNOWN_FAILURES = new Set<string | undefined>([
  "INVALID_ARGUMENT",
  "PERMISSION_DENIED",
  "UNAUTHENTICATED",
  "QUOTA_EXCEEDED",
  "UNAVAILABLE",
  "INTERNAL",
]);

const encoder = new TextEncoder();

function base64Url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function importPrivateKey(pem: string): Promise<CryptoKey> {
  const base64 = pem
    .replaceAll("\\n", "\n")
    .split("\n")
    .filter((line) => !line.startsWith("-----"))
    .map((line) => line.trim())
    .join("");
  const der = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
}

async function assertion(account: ServiceAccount, tokenUri: string): Promise<string> {
  const issuedAt = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: account.client_email,
    scope: MESSAGING_SCOPE,
    aud: tokenUri,
    iat: issuedAt,
    exp: issuedAt + ASSERTION_LIFETIME_SECONDS,
  };
  const signingInput = `${base64Url(encoder.encode(JSON.stringify(header)))}.${base64Url(encoder.encode(JSON.stringify(claims)))}`;
  const key = await importPrivateKey(account.private_key);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(signingInput));
  return `${signingInput}.${base64Url(new Uint8Array(signature))}`;
}

export async function accessToken(account: ServiceAccount, fetchFn: typeof fetch = fetch): Promise<string> {
  const tokenUri = account.token_uri || DEFAULT_TOKEN_URI;
  const response = await fetchFn(tokenUri, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded", accept: "application/json" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: await assertion(account, tokenUri),
    }),
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) {
    await response.body?.cancel();
    throw new Error(`Google token endpoint answered HTTP ${response.status}`);
  }
  const { access_token } = await response.json();
  if (typeof access_token !== "string") throw new Error("Google token endpoint returned no access_token");
  return access_token;
}

function messageBody(token: string, message: PushMessage) {
  return {
    message: {
      token,
      notification: message.body ? { title: message.title, body: message.body } : { title: message.title },
      ...(Object.keys(message.data).length > 0 ? { data: message.data } : {}),
      android: { priority: "HIGH", notification: { channel_id: "tana_default" } },
      apns: { headers: { "apns-priority": "10" }, payload: { aps: { sound: "default" } } },
    },
  };
}

async function errorStatus(response: Response): Promise<string | undefined> {
  try {
    return (await response.json())?.error?.status;
  } catch {
    return undefined;
  }
}

export async function send(
  token: string,
  message: PushMessage,
  projectId: string,
  bearer: string,
  fetchFn: typeof fetch = fetch,
): Promise<PushOutcome> {
  let response: Response;
  try {
    response = await fetchFn(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${bearer}`,
        "content-type": "application/json; charset=utf-8",
        accept: "application/json",
      },
      body: JSON.stringify(messageBody(token, message)),
      signal: AbortSignal.timeout(10_000),
    });
  } catch (e) {
    console.warn(`FCM send to ${tokenHint(token)} failed: ${(e as Error).name}`);
    return "failed";
  }
  if (response.ok) {
    await response.body?.cancel();
    return "delivered";
  }
  const status = await errorStatus(response);
  const outcome = GONE.has(status) || (!KNOWN_FAILURES.has(status) && response.status === 404) ? "unregistered" : "failed";
  console.warn(`FCM refused ${tokenHint(token)} with HTTP ${response.status} (${status ?? "no status"}) -> ${outcome}`);
  return outcome;
}

export function tokenHint(token: string): string {
  return `${token.slice(0, 8)}…(${token.length})`;
}
