# Notifications

Two things that are easy to confuse:

- **The notification centre** is a list of rows in the database (`notifications`, added in `V4`). It
  is the durable channel: a row is stored whether or not any device could be reached, and the bell
  in the app bar shows how many are unread.
- **A push** is a best-effort wake-up for a device that is registered and allowed. It can fail —
  no permission, no token, an uninstalled app, FCM down — and none of those failures lose the
  notification.

Everything below is gated by the `notifications` feature flag, which **ships off**. With the flag
off the routes answer `503 feature_disabled`, the bell is not drawn and nothing registers.

## The shape of it

```
Android ─┐                              ┌─ POST /api/v1/notifications/devices   (token, platform)
  iOS ───┼─ core:notifications ── HTTP ──┤  GET  /api/v1/notifications           (the centre)
  Web ───┘   PushRegistrar              └─ POST /api/v1/notifications/{id}/read
                                                        │
                                           NotificationService
                                           ├── notifications table   (always)
                                           └── PushSender ─ FCM HTTP v1 ─> the device
```

| Piece | Where |
|---|---|
| Wire contract (paths, DTOs, limits, channel id) | `shared/.../notifications/` |
| Permission, token, registration lifecycle | `core:notifications` |
| The centre (list, badge, mark read) | `feature/Notifications`, `core:notifications` (`NotificationBadge`) |
| Routes, storage, fan-out | `server/.../notifications/` |
| FCM transport | `server/.../notifications/push/` |
| Device registrations | `device_tokens` (`V8__push_notifications.sql`) |

## The API

All of it needs a signed-in account (`Authorization: Bearer <jwt>`).

| Method | Path | Answers |
|---|---|---|
| `GET` | `/api/v1/notifications?limit=&before=` | A page, newest first, plus the account's `unreadCount` and a `nextBefore` cursor |
| `GET` | `/api/v1/notifications/unread-count` | Just the count, for the badge |
| `POST` | `/api/v1/notifications/{id}/read` | The new count, or `404 notification_not_found` if nothing unread has that id |
| `POST` | `/api/v1/notifications/read-all` | The new count (0) |
| `POST` | `/api/v1/notifications/devices` | `204`; registers or moves this device's token |
| `POST` | `/api/v1/notifications/devices/unregister` | `204`; always succeeds |
| `POST` | `/api/v1/notifications/send` | **admin only** — stores a notification and pushes it |

Paging is keyset, not offset: `before` is the `createdAtEpochMillis` of the oldest row you have, so
a notification arriving mid-scroll cannot shift the page under the user. A cursor comes back only
when the page was full.

### Sending one (and smoke-testing delivery)

```bash
TOKEN=$(curl -s localhost:8081/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ops@tana.local","password":"<admin password>"}' | jq -r .accessToken)

curl -s localhost:8081/api/v1/notifications/send -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"userId":"<uuid>","type":"order","title":"Đơn hàng đã xác nhận","body":"Đơn #1234","data":{"orderId":"1234"}}'
```

`{"notificationId":"…","devices":2,"delivered":2,"removed":0}` — `removed` counts tokens FCM
rejected as dead, which this call deleted. `503 push_unavailable` means the row was stored but no
device could be reached; the id is in the message.

## Two rules worth knowing before changing any of this

**A token belongs to a device, not to an account.** `device_tokens.token` is globally unique and
registering it *moves* it to the signed-in user. Without that, the previous user of a shared phone
keeps receiving the next user's notifications. The client re-registers with `force = true` whenever
the access token changes, which is what makes the move happen.

**A dead token is deleted, not retried.** FCM answers `UNREGISTERED` (uninstalled) or
`SENDER_ID_MISMATCH` (a token from another Firebase project); both are permanent, so the row goes.
Everything else is either retryable (`UNAVAILABLE`, `QUOTA_EXCEEDED`, `INTERNAL`, 5xx, 429) or a
configuration mistake (`INVALID_ARGUMENT`, `PERMISSION_DENIED`, `UNAUTHENTICATED`) and is only
logged.

## Server configuration

```
FCM_SERVICE_ACCOUNT_JSON=   # the service-account key, inline (escaped newlines are fine)
FCM_SERVICE_ACCOUNT_FILE=   # ...or a path to it; either one is enough
FCM_PROJECT_ID=             # optional, defaults to the key's own project_id
```

Get the key from the Firebase console: *Project settings → Service accounts → Generate new private
key*. It is a credential — keep it in the hosting platform's secret store, never in the repo.

With neither variable set the server uses `DevPushSender`, which logs what it would have sent
(title and a token prefix, never the token) and reports every send as delivered. The notification
centre is fully usable in that mode; only the device wake-up is missing. Unlike the OTP provider
this does **not** refuse to boot in production, because the feature flag ships off — a production
server that does not use notifications is a legitimate state. It logs a warning instead.

The server authenticates to FCM with a self-signed JWT exchanged for an access token
(`FcmAccessTokens`), cached until a minute before it expires. No Google client library is needed.

## Clients

### Android

Everything except the token itself is in `core:notifications`; the token comes from the Firebase
SDK, and the app shell owns the pieces that need a manifest or an Activity
(`androidApp/.../notifications/`).

1. Add the Android app to a Firebase project and drop `google-services.json` into `androidApp/`.
   It is gitignored, and the build applies the `google-services` plugin **only if the file is
   there** — a clone without one still builds and runs, with push unavailable.
2. Nothing else: the manifest already declares `POST_NOTIFICATIONS`, the messaging service and the
   default channel (`tana_default`, which the server names in every message — Android 8+ drops a
   notification whose channel does not exist).

The runtime permission is requested from the notification centre, never at launch: the system
dialog appears **once per install**, and spending it on a cold start is how an app ends up with
notifications permanently refused.

### iOS — **not verified**

The Kotlin side is written (`IosPushPermissions`, `IosPushTokens`) but has never been compiled:
this repo's iOS target needs macOS, and the work was done on Windows. Treat the Swift steps below
as a starting point, not a tested recipe.

1. Add the Firebase iOS SDK (SPM: `firebase-ios-sdk`, product `FirebaseMessaging`) to `iosApp`,
   add `GoogleService-Info.plist`, and enable *Push Notifications* plus the *Remote notifications*
   background mode in the target's capabilities.
2. In the `AppDelegate`, call `FirebaseApp.configure()`, set
   `Messaging.messaging().delegate = self`, and hand the token to Kotlin:

   ```swift
   func messaging(_ messaging: Messaging, didReceiveRegistrationToken token: String?) {
       IosPushTokenBridge.shared.onToken(value: token)
   }
   ```

   Kotlin asks for the permission and calls `registerForRemoteNotifications()` itself; the APNs
   token reaches Firebase through the SDK's own `didRegisterForRemoteNotificationsWithDeviceToken`
   swizzling (or forward it manually if swizzling is disabled).

iOS receives the push through FCM's APNs relay, so the server still needs only the one FCM key —
plus the APNs auth key uploaded to Firebase, which is a console step, not a server setting.

### Web

`composeApp/src/webMain/resources/index.html` and `firebase-messaging-sw.js` both carry a
commented-out Firebase block. Uncomment both, paste the web app's config and the Web Push
certificate key pair (`window.TANA_PUSH_VAPID_KEY`), and keep the two copies in step — a service
worker runs outside the page and cannot read its config.

None of those values is a secret; they identify the project to the browser. The service worker has
to stay at the site root, which is why it sits next to `index.html`.

The Kotlin side reaches the browser through `js(...)` snippets rather than `external` declarations
on purpose: a missing `external` makes the whole wasm module fail to instantiate (a white screen),
while a snippet can test for the API and answer "no push". With the block commented out,
`PushTokens.current()` returns null and registration is skipped.

**The web build keeps nothing across a reload** — `core:cache` is in-memory on wasm — so the
session, and with it the registration, starts fresh on every refresh.

### Desktop

No push transport: `PushPermissions.unsupported` and `NoPushTokens`. The notification centre works,
because it is an ordinary authenticated list.

## Still to do

- **Sign-out does not call `PushRegistrar.unregister()`**, because the app has no sign-out flow yet
  (the Profile screen is a placeholder). Wire it in when that screen lands, or the next user of the
  device will keep receiving the previous user's pushes until a token rotation or a re-registration
  moves it.
- Tapping a row marks it read but does not navigate: the payload is carried in
  `NotificationItem.data` and nothing consumes it yet.
- No per-type preferences (mute promotions, keep order updates). The flag is all-or-nothing per
  user today.
- No scheduled or batched sends, and no topics — `send` addresses one account at a time.
