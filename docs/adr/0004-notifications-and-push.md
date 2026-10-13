# 4. The notification centre is the channel; a push is a best-effort wake-up

Date: 2026-10-11

## Status

Accepted

## Context

`V4` already created a `notifications` table for "the bell icon and its red dot", but nothing read
or wrote it, and there was no way to reach a device at all. Adding that means four separate
problems, and they are easy to conflate:

1. Storing and reading notifications per account.
2. Getting a registration token from three very different platforms (FCM on Android, APNs through
   Firebase on iOS, the Web Push API in a browser) and keeping track of which account a device
   belongs to.
3. Asking the user for permission — once, because on Android 13+ and iOS the system dialog appears
   **once per install**, and a refusal can only be undone in the system settings.
4. Delivering to a transport that routinely answers "this device is gone".

The risk that shapes most of what follows is silent loss. A push is not a delivery guarantee: the
user may have refused permission, the token may be stale, the browser may have blocked the service
worker, FCM may be down, the device may be off. If a notification only exists as a push, all of
those are indistinguishable from "we never told you".

## Decision

**The row is written first, and a push is sent after.** `NotificationService.send` stores the
notification and only then fans out to the account's devices. Delivery counts come back to the
caller (`devices`, `delivered`, `removed`) but a failure to reach a device is never a failure of
the call: the notification is already in the centre, where the user will see it. Only the case
where *every* device failed for a non-device reason answers `503 push_unavailable`, and it names
the stored notification's id so an operator can tell a misconfigured sender from a lost message.

**A token identifies a device, not an account.** `device_tokens.token` is globally unique and
registering it *moves* it to the signed-in user. The alternative — a row per (user, token) — leaves
the previous user of a shared phone receiving the next user's notifications. The clients
re-register with `force = true` whenever the access token changes, which is what triggers the move.

**A token the provider rejects is deleted.** `UNREGISTERED` and `SENDER_ID_MISMATCH` are permanent,
so the row goes on the spot rather than being retried on every send forever. Retryable answers
(`UNAVAILABLE`, `QUOTA_EXCEEDED`, `INTERNAL`, 5xx, 429) and configuration mistakes
(`INVALID_ARGUMENT`, `PERMISSION_DENIED`, `UNAUTHENTICATED`) are only logged — there is no retry
queue, by choice: a notification worth resending is worth sending as a fresh one.

**The permission is asked for in the notification centre, never at launch.** A cold-start prompt
spends the one dialog the app will ever get on a user who has no idea what it is for, and the usual
result is a permanent refusal. `registerIfPossible()` — which runs on launch and after each
sign-in — only ever registers a permission that was *already* granted; only
`requestPermissionAndRegister()`, behind an explicit button, can prompt.

**One flag, `notifications`, ships off, and the check lives in the service.** It gates the routes
and, inside `NotificationService.send`, the fan-out itself — resolved for the **recipient**, not
for whoever triggered the send, so a per-user rollout means "this person receives notifications".
Any future internal caller (an order service, a scheduled job) is therefore gated too, and
switching the feature off also stops the history filling up behind the user's back.

**FCM HTTP v1 is the only transport, for all three clients.** iOS goes through FCM's APNs relay, so
the server holds one credential instead of two, and the clients all hand over the same opaque
string. The OAuth2 token is minted from the service-account key with a self-signed JWT
(`FcmAccessTokens`, ~60 lines) rather than pulling in the Google auth library for one grant. With
no credentials configured the server uses `DevPushSender`, which logs instead of sending — and
unlike the OTP provider it does **not** refuse to boot in production, because this feature ships
off and a production server that does not use notifications is a legitimate state.

**The vendor SDK stays out of the shared modules.** `core:notifications` owns the contract, the
permission flow, the registration lifecycle and the badge; the Firebase dependency and the token
itself are the app shell's business. Android's token comes from `firebase-messaging` in
`core:notifications`' android source set, but the manifest service, the channel and the Activity
needed for the permission dialog live in `androidApp`; iOS receives its token from the Swift
`AppDelegate` through `IosPushTokenBridge`; the browser gets it from the Firebase compat SDK the
page loads itself. Every one of them degrades to "no token" when nothing is configured, which is
why `google-services.json` being absent is a working build rather than a broken one.

**Browser interop goes through `js(...)` snippets, not `external` declarations.** A missing
`external` binding makes the whole wasm module fail to instantiate — a white screen for the entire
app — whereas a snippet can test for the API and answer "no push".

## Consequences

- A notification always exists in one place, so "did the user get it?" is answerable by reading a
  row rather than by trusting a transport.
- Push delivery is unverified end to end. The FCM wire format, the error mapping and the signed
  assertion are covered against a local fake; nothing here has talked to Google's servers, and the
  iOS source has never been compiled (it needs macOS). Both are stated in `docs/notifications.md`.
- There is no retry and no delivery receipt. A device that was off when a notification was sent
  learns about it by opening the app.
- Sign-out does not yet detach the device: `PushRegistrar.unregister()` exists and the endpoint
  works, but the app has no sign-out flow to call it from. Until that lands, a shared device keeps
  the previous account's registration until the next sign-in moves it.
- Per-type preferences (mute promotions, keep order updates) do not exist; the flag is
  all-or-nothing per user.
- `notifications.title` and `body` gained length constraints in `V8`, so a caller that used to be
  able to store arbitrary text now gets a `400` from the API instead of a `500` from the column.
