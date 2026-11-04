# Notifications

Two things that are easy to confuse:

- **The notification centre** is a list of rows in the database (`notifications`, added in `V4`). It
  is the durable channel: a row is stored whether or not any device could be reached, and the bell
  in the app bar shows how many are unread.
- **A push** is a best-effort wake-up for a device that is registered and allowed. It can fail —
  no permission, no token, an uninstalled app, FCM down — and none of those failures lose the
  notification.

Everything below is gated by the `notifications` feature flag, which **ships off**. With the flag
off the functions answer `503 feature_disabled`, the bell is not drawn and nothing registers.

## The shape of it

```
Android ─┐                                        ┌─ register_device      (token, platform)
  iOS ───┼─ core:notifications ── /rest/v1/rpc/ ──┤  notifications_list   (the centre)
  Web ───┘   PushRegistrar                        └─ notifications_mark_read
                                                              │
                                    notifications table (always) ── insert trigger
                                                                         │ pg_net
                                                     Edge Function push ─ FCM HTTP v1 ─> the device
```

| Piece | Where |
|---|---|
| DTOs, data keys, channel id | `shared/.../notifications/` |
| Permission, token, registration lifecycle | `core:notifications` |
| The centre (list, badge, mark read) | `feature/Notifications`, `core:notifications` (`NotificationBadge`) |
| Functions, storage, the trigger | `supabase/migrations/20261104171746_notifications.sql`, `20261104190000_push_webhook.sql` |
| FCM transport | `supabase/functions/push/` |
| Device registrations | `device_tokens` (`V8__push_notifications.sql`) |

## The functions

All of them need a signed-in account and are called as `POST /rest/v1/rpc/<name>`.

| Function | Answers |
|---|---|
| `notifications_list(limit?, before?)` | A page, newest first, plus the account's `unreadCount` and a `nextBefore` cursor |
| `notifications_unread_count()` | Just the count, for the badge |
| `notifications_mark_read(id)` | The new count, or `404 notification_not_found` if nothing unread has that id |
| `notifications_mark_all_read()` | The new count (0) |
| `register_device(token, platform, provider?, locale?, appVersion?)` | Registers or moves this device's token |
| `unregister_device(token)` | Always succeeds |
| `admin_send_notification(userId, title, type?, body?, data?)` | **`OPERATE_SYSTEM` only** — stores a notification; the trigger pushes it |

Paging is keyset, not offset: `before` is the `createdAtEpochMillis` of the oldest row you have, so
a notification arriving mid-scroll cannot shift the page under the user. A cursor comes back only
when the page was full.

### Sending one (and smoke-testing delivery)

With an administrator's access token (`<project>` is the project URL, `<publishable key>` the key
in `Constants`):

```bash
curl -s <project>/rest/v1/rpc/admin_send_notification \
  -H "apikey: <publishable key>" -H "Authorization: Bearer <admin access token>" \
  -H 'Content-Type: application/json' \
  -d '{"userId":"<uuid>","type":"order","title":"Đơn hàng đã xác nhận","body":"Đơn #1234","data":{"orderId":"1234"}}'
```

The answer carries the new `notificationId`, the number of registered `devices`, and
`delivered = 0, removed = 0`: the push happens afterwards, through the trigger, so this call
cannot know the outcome. The `push` function's log has it (`devices=… delivered=… removed=…`), and
`notifications.sent_push_at` is stamped when at least one device took it. A recipient with the
flag off is `503 feature_disabled`.

## Rules worth knowing before changing any of this

**A token belongs to a device, not to an account.** `device_tokens.token` is globally unique and
registering it *moves* it to the signed-in user. Without that, the previous user of a shared phone
keeps receiving the next user's notifications. The client re-registers with `force = true` whenever
the access token changes, which is what makes the move happen.

**A tap goes where the notification is about.** The centre routes on `NotificationItem.data`: a
`booking` notification — a reminder, a confirmation or a decline — opens the guest's own visits on
that booking (`NotificationData.BOOKING_ID`, written by the database and read by the app, so the two
keys cannot drift). An entry already read still opens; reading it is not what the tap was for, and
a notification with no payload to act on only marks itself read.

**A dead token is deleted, not retried.** FCM answers `UNREGISTERED` (uninstalled) or
`SENDER_ID_MISMATCH` (a token from another Firebase project); both are permanent, so the row goes.
Everything else is either retryable (`UNAVAILABLE`, `QUOTA_EXCEEDED`, `INTERNAL`, 5xx, 429) or a
configuration mistake (`INVALID_ARGUMENT`, `PERMISSION_DENIED`, `UNAUTHENTICATED`) and is only
logged.

## Backend configuration

The trigger on `notifications` reads two Vault secrets, `push_url` (the `push` function's URL) and
`push_secret`. With either missing it posts nothing; the row is stored either way, and a failure
to enqueue never rolls the insert back.

The `push` function's secrets (Edge Function secrets, or `supabase/functions/.env` when serving
locally):

```
PUSH_WEBHOOK_SECRET=        # the same value as Vault's push_secret; calls without it are refused
FCM_SERVICE_ACCOUNT_JSON=   # the service-account key, inline
FCM_PROJECT_ID=             # optional, defaults to the key's own project_id
```

Get the key from the Firebase console: *Project settings → Service accounts → Generate new private
key*. It is a credential — keep it in Edge Function secrets, never in the repo.

Without `FCM_SERVICE_ACCOUNT_JSON` the function only logs what it would have sent (the title and
a token prefix, never the token) and answers `delivered = 0`. The notification centre is fully
usable in that mode; only the device wake-up is missing.

The function authenticates to FCM with a JWT it signs itself (RS256 through WebCrypto) and
exchanges for an access token. No Google client library is needed.

## Clients

### Android

Everything except the token itself is in `core:notifications`; the token comes from the Firebase
SDK, and the app shell owns the pieces that need a manifest or an Activity
(`androidApp/.../notifications/`).

1. Add the Android app to a Firebase project and drop `google-services.json` into `androidApp/`.
   It is gitignored, and the build applies the `google-services` plugin **only if the file is
   there** — a clone without one still builds and runs, with push unavailable.
2. Nothing else: the manifest already declares `POST_NOTIFICATIONS`, the messaging service and the
   default channel (`tana_default`, which the `push` function names in every message — Android 8+ drops a
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

iOS receives the push through FCM's APNs relay, so the backend still needs only the one FCM key —
plus the APNs auth key uploaded to Firebase, which is a console step, not a backend setting.

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

The web build keeps the session in `localStorage`, so a reload keeps the registration too.

### Desktop

No push transport: `PushPermissions.unsupported` and `NoPushTokens`. The notification centre works,
because it is an ordinary authenticated list.

## Still to do

- **Sign-out does not call `PushRegistrar.unregister()`.** Settings ends the session, but the
  device keeps its registration, so the next user of that device would keep receiving the previous
  user's pushes until a token rotation or a re-registration moves it.
- Only `booking` notifications route anywhere. An order or a promotion still just marks itself
  read, because neither has a screen to open yet.
- No per-type preferences (mute promotions, keep order updates). The flag is all-or-nothing per
  user today.
- No batched sends and no topics — `admin_send_notification` addresses one account at a time.
