import type { ServiceAccount } from "./fcm.ts";

export async function testServiceAccount(): Promise<{ account: ServiceAccount; publicKey: CryptoKey }> {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  const base64 = btoa(String.fromCharCode(...der)).replace(/(.{64})/g, "$1\n");
  return {
    account: {
      project_id: "tana-test",
      client_email: "push@tana-test.iam.gserviceaccount.com",
      private_key: `-----BEGIN PRIVATE KEY-----\n${base64}\n-----END PRIVATE KEY-----\n`,
      token_uri: "https://token.test/token",
    },
    publicKey: pair.publicKey,
  };
}
