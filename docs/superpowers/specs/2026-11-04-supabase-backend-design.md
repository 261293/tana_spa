# Backend on Supabase — design

Date: 2026-11-04 · Status: draft, awaiting owner approval

## Why

The owner wants the whole app to run for as little as possible, ideally $0. The Ktor server is a JVM
fat jar, and no free host the owner accepted can run one (Cloudflare Containers needs Workers Paid,
Cloud Run was declined, Supabase Compute is private alpha). The database already lives on Supabase
free. This design replaces the Ktor server with Supabase's own platform features so that the
database, auth and business rules all run inside the one free project.

Nothing is in production. There are no users or data to preserve, and the client may change.

## Decisions the owner made

| Decision | Choice |
|---|---|
| Scope | Port **everything** the server does, including phone OTP (Twilio) and push (FCM), both of which ship switched off |
| Approach | **Lean on Supabase**: Supabase Auth, the Data API guarded by RLS, Postgres functions for rules, Edge Functions only where SQL cannot do the job (as it turned out: push, and creating accounts) |
| Email confirmation on sign-up | **Off**. Signing up signs you in, as today |
| Web hosting | Stays on Cloudflare Workers static assets (already live at `https://tana-spa-web.spa-tana.workers.dev`) |
| Spa location | Vietnam; timezone `Asia/Ho_Chi_Minh` |
| Seed content | The spa is **Tana Spa** in Hồ Chí Minh City. Keep the current menu, therapists and offers; reprice them in dong |
| Currency | Prices are **stored and shown in dong in every language**. Dollars disappear from the app |

## Goals

- Every behaviour the Ktor server has today is reproduced, with the same error codes.
- All four client targets (Android, iOS, desktop JVM, web wasmJs) talk to Supabase directly.
- Running cost $0 on Supabase free and Cloudflare free.
- Every rule that protects data is enforced in the database, and is tested there.

## Non-goals

- Password reset and email verification (no email sender exists; the owner chose not to add one).
- Google/Apple sign-in. `auth_identities` exists but no route ever used it.
- Shop, cart and orders. The tables exist (V2, V3) but the server has no routes for them.
- The home feed. `FeedRemoteApiImpl` returns stub data and stays as it is.
- `/demo` routes. They are a hello-world and are dropped.

## Anti-goals (would make this a failure even if it works)

- Any table readable or writable by a role that should not see it. The anon key is public by design.
- A business rule enforced only in the client (booking overlaps, cancel window, role changes, voucher limits).
- A secret (service role key, FCM key, Twilio key) shipped in the client or the web bundle.

## Constraints

- **Supabase free limits** (checked on supabase.com docs on 2026-11-04): 500 MB database, 500k Edge
  Function invocations a month, Edge Functions 2 s CPU / 256 MB / 150 s per call, and a project
  that sees little activity for about a week is paused.
- **The database is already live**: project `jnlonvutjugxxxsfpeom`, region `ap-southeast-1`,
  Postgres 17.11. V1–V12 were applied verbatim, then two Supabase-only migrations:
  `lock_down_data_api` (RLS enabled on every table with no policies; all grants revoked from
  `anon` and `authenticated`, including default privileges; `v_user_history` set to
  `security_invoker`) and `pin_function_search_path`. **This design deliberately re-opens access,
  one table and one verb at a time.** Any table created later needs RLS enabled explicitly.
- **Client library**: `supabase-kt` 3.x. Its Auth and Postgrest module READMEs list JVM, Android,
  wasm-js and iOS as supported. It recommends Ktor 3.4; this project uses Ktor 3.6.0. Compatibility
  is **unverified** until it builds.
- **Mobile apps point at localhost today.** `Constants.apiBaseUrl` hard-codes `10.0.2.2:8081` and
  `localhost:8081`; only the web build can be pointed elsewhere (`window.TANA_API_BASE_URL`).
- No phone-number normalisation exists in the client; the server did it with libphonenumber
  (default region VN). Supabase Auth requires E.164.

## Approaches considered

| | Approach | Trade-off | Outcome |
|---|---|---|---|
| 1 | One Edge Function `api` (TypeScript, Hono) behind the unchanged `/api/v1/...` contract, keeping the custom auth | Client only changes its base URL; but ~6,200 lines of server rewritten, auth and refresh-token rotation re-implemented by hand | Recommended by the assistant, **not chosen** |
| 2 | Supabase Auth + Data API with RLS + Postgres functions (RPC) + one Edge Function for push | Least backend code, platform handles sessions; but every client data layer changes, and the Data API must be re-opened safely with per-table policies | **Chosen by the owner** |
| 3 | One Edge Function per module | URLs lose their common prefix so every client call changes anyway, plus a cold start per module | Rejected |

## Architecture

### Where each piece of today's server goes

| Today (Ktor) | Tomorrow (Supabase) |
|---|---|
| `POST /auth/register`, `/login`, `/refresh`, `/logout` | Supabase Auth via `supabase-kt` (`signUpWith(Email)`, `signInWith(Email)`, session refresh and sign-out handled by the SDK) |
| `GET /auth/me` | `select` on `users` where `id = auth.uid()` |
| PBKDF2 hashing, `refresh_tokens`, `RefreshTokenService`, the token sweep | Removed. Supabase Auth owns credentials, rotation and reuse detection |
| Welcome voucher granted after sign-up (`PromotionService.grant(NEW_GUEST_CODE)`) | Inside the sign-up trigger (below), wrapped so that a failure to grant never fails the sign-up, matching today's behaviour |
| Seeding admins/owner from `SEED_*` env vars | Removed. A short runbook: create the first admin in Supabase Auth, then set `users.role` with SQL. Every later account is created from the app (next row) |
| `POST /api/v1/accounts` (owner/admin creates an account with a password and a role; used by `DeskRepositoryImpl.createAccount` from `AdminMenuViewModel`) | Edge Function `accounts` *(assistant's call, see Accounts below)* |
| `PATCH /api/v1/accounts/{id}/role` | RPC, same rules (`role_not_allowed`, `own_role_unchangeable`, `unknown_account`) |
| `POST /auth/phone/otp/send` and `/verify`, `TwilioVerifyProvider`, `PhoneVerificationService` | Supabase Auth phone change: `updateUser(phone = ...)` sends the code, `verifyPhoneOtp(type = PHONE_CHANGE)` checks it. SMS goes through Supabase's Twilio Verify provider, configured in the dashboard |
| Catalogue, offers, `my bookings`, notifications list, app-update config | Direct `select` through the Data API, each allowed by an RLS policy |
| Availability, booking create/cancel, owner actions, offer check, admin codes, role changes, flag writes, device tokens, marking read, admin send | Postgres functions called with `rpc()` |
| `FcmPushSender`, `FcmAccessTokens` | Edge Function `push`, invoked by a database webhook on `notifications` insert |
| Hourly reminder sweep coroutine | `pg_cron` job calling a SQL function |
| Static admin page served at `/admin/` | Same page rewritten against `supabase-js`, served by the Cloudflare Worker at `/admin/` |
| Login, refresh and OTP rate limits (Ktor `RateLimit`; the only routes that had any) | Supabase Auth's built-in rate limits |
| `server/` module, its Dockerfile, `fly.toml` | Deleted once parity is reached |

### Identity

- `users` stays the profile table. Its `id` becomes the `auth.users` id.
- A trigger on `auth.users` insert creates the `users` row (`role = 'customer'`, `full_name` and
  optional `phone` from the sign-up metadata), then grants the welcome voucher in its own
  exception block.
- A trigger on `auth.users` update copies a confirmed phone (`phone`, `phone_confirmed_at`) into
  `users.phone` / `users.phone_verified_at`. V5's rule that a phone is unique only among verified
  users still holds.
- `users.password_hash` and `refresh_tokens` are dropped. `phone_verifications` is dropped too:
  Supabase Auth keeps its own record of codes. **Lost with it**: per-country allow/block lists
  (`OTP_ALLOWED_COUNTRIES`/`OTP_BLOCKED_COUNTRIES`) and the per-country fraud dashboard.
- Roles are read from `users.role` inside policies and functions through one
  `security definer` helper (`app_role()` or similar), not from JWT claims, so a role change takes
  effect on the next request rather than at the next token refresh.
- Access is decided by **permission**, not by role name. `shared/.../auth/SpaPermissions.kt` is the
  matrix (admin: every permission; owner: a subset including `MANAGE_ACCOUNTS`; staff:
  `MANAGE_BOOKINGS`; customer: none), plus `creatableBy` (admin creates admin/owner/staff/customer,
  owner creates staff/customer) and `canChangeRole`. A SQL `has_permission(...)` mirrors it. The
  Kotlin and SQL copies can drift, so a pgTAP test enumerates the whole matrix and must be updated
  together with `SpaPermissions.kt`.
- Users cannot write their own `users.role`. Profile edits, if any, go through a function or a
  column-limited grant.

### Phone numbers *(assistant's call)*

A small SQL function normalises the shape the app accepts today to E.164, assuming Vietnam for
numbers written with a leading 0 (`0912345678` → `+84912345678`). The client applies the same rule
from `shared/` before calling `updateUser`. This replaces libphonenumber's full validation, which
is the cost of having no server.

### Feature flags

- Resolution was: per-user override → global row → `FEATURE_<KEY>` env → compiled default.
  **The env layer is lost**, because there is no server environment. Now: override → global row →
  compiled default, with the compiled default applied in the client from `shared/`'s catalogue.
- An RPC returns the resolved map for the caller (with no user, globals only).
- **Server-side gates**:
  - `notifications`: checked for the **recipient** inside the send path, as `NotificationService.send`
    does today (a send to a user with the flag off is refused), and inside the notification RPCs.
  - `app_update_gate`: the config read stays open; the client already fails open.
  - `phone_otp_verification`: Supabase Auth cannot consult our table. The flag is enforced by the
    SMS provider being **disabled in the dashboard while the flag is off**. Turning the feature on
    means doing both. The client also hides the flow when the flag is off.

### Postgres functions (RPC)

- `security definer`, `search_path = ''`, every name schema-qualified.
- Each starts by checking `auth.uid()` and, where needed, the role.
- Failures `raise exception` with the **existing error-code string as the message**
  (`slot_taken`, `too_late_to_cancel`, `own_role_unchangeable`, ...), so `shared/`'s error-code
  constants stay the contract. HTTP status codes no longer carry meaning; client code that branches
  on a status must branch on the code instead.
- *(Assistant's call)* Functions return `jsonb` shaped like the existing `shared/` DTOs, so the
  client keeps decoding into the serializable classes it already has.
- Booking creation does the overlap check and the insert in one transaction, with a lock that
  stops two guests taking the same therapist slot at once.
- `EXECUTE` is granted only to the roles that should call each function (`authenticated`, and
  `anon` only for public reads such as availability and offer check), and revoked from `PUBLIC`.

### Seed content and currency

Today the catalogue is priced in whole dollars in columns named `*_vnd` (V9 admits this), and the
client converts to dong for Vietnamese readers with `formatPrice(usd)` at the fixed display rate
`VND_PER_USD = 25_000` (`shared/.../helpers/Money.kt`). After this change the columns hold dong,
as their names always claimed.

A new migration updates the live seed rows:

| What | Before | After |
|---|---|---|
| Branch | Jade Bathhouse, 1452 Valencia Street, San Francisco | **Tana Spa**, city **TP. Hồ Chí Minh**, `address_line` = `Đang cập nhật` *(a visible placeholder; the owner has not given the address, and none is invented)*; hours 09:00–21:00 unchanged |
| Warm Stone Massage | 165 | 4.125.000 |
| Jade Roller Facial | 120 | 3.000.000 |
| Deep Tissue Massage | 140 | 3.500.000 |
| Matcha Body Polish | 98 | 2.450.000 |
| Herbal Compress Ritual | 150 | 3.750.000 |
| MIDWEEK15 (fixed) | 15 off, minimum 120 | 375.000 off, minimum 3.000.000; the English title and description rewritten in dong; the code keeps its name |
| JADE20, FIRSTVISIT | percent | unchanged |

The dong figures are the dollar prices × 25.000, i.e. exactly what Vietnamese readers see today, so
nobody's quoted price moves. Therapists and the Vietnamese texts (V11, V12) are unchanged.

Client: `formatPrice` always formats dong (`formatVnd`) whatever the language; `VND_PER_USD` and
`usdToVnd` are deleted, so the compiler finds every caller (21 call sites across `core:ui` and
`feature/Booking`, plus `SpaMoneyFormatTest` and `SpaPromoTextTest`). Any promo text built from a
fixed voucher value formats dong too. *(Assistant's call)* Amounts stay `Int` in the DTOs: the
largest seed price is 4.125.000 and `Int` holds up to about 2,1 tỷ đồng.

### Accounts *(assistant's call)*

Creating an account for someone else needs Supabase's Auth admin API, which needs the service role
key. A Postgres function cannot create an `auth.users` row properly, and calling `signUpWith` from
the client would sign the owner in as the new account. So a second Edge Function, `accounts`:

- verifies the caller's JWT, reads the caller's role from `users`, and refuses unless the caller has
  `MANAGE_ACCOUNTS` and `canCreate(callerRole, requestedRole)` holds (`role_not_allowed`);
- calls `auth.admin.createUser` with the email, password and display name, already confirmed;
- sets `users.role` on the row the sign-up trigger created, and returns the created-account DTO;
- answers `email_taken` and `invalid_request` as the Ktor route does.

The service role key lives only in Edge Function secrets. `accounts` is a public URL, so its role
checks get their own tests, including every disallowed caller/role pair.

### Spa timezone

`SPA_TIMEZONE` becomes a single-row `spa_settings` table, so the owner can change it without a
deploy. It is seeded with **`Asia/Ho_Chi_Minh`**: the spa is in Vietnam (owner's decision). This
replaces the Ktor code's fallback of `America/Los_Angeles`. The timezone drives availability slots,
"today", the two-hour cancel window and reminder timing.

### Environments *(assistant's call)*

- One remote project, `jnlonvutjugxxxsfpeom`, becomes production. The second free project slot is
  left unused.
- Development and all tests run against the local stack (`supabase start`, Docker). Debug builds
  of every platform point at the local stack; release builds at the remote project.
- Migrations reach the remote only after their tests pass locally. The versions already recorded
  remotely are read from `supabase_migrations.schema_migrations` when `supabase/migrations/` is
  created.
- Settings that live outside SQL (Auth options, Edge Function secrets, the webhook) are applied by
  the assistant through the Supabase CLI, MCP or Management API where possible. Anything that
  needs the owner's login or money (Twilio, Firebase) is a checklist for the owner.
- The post-deploy smoke test uses a throwaway account, deleted afterwards.

### Push

- Database webhook on `notifications` insert → Edge Function `push`.
- `push` loads the recipient's `device_tokens`, signs a Google service-account JWT (RS256 via
  WebCrypto), calls FCM HTTP v1, deletes tokens FCM reports as unregistered, and stamps
  `sent_push_at`. A delivery failure is not an error, as today: the row still shows in the app.
- Secrets live in Edge Function secrets (`FCM_SERVICE_ACCOUNT_JSON`). With no secret configured,
  `push` only logs, like today's dev sender.
- The function rejects calls without the webhook's secret header; it is not a public endpoint.

### Reminders

`pg_cron`, hourly, calls a SQL function that claims due bookings by stamping `reminder_sent_at`
(the same once-only claim V10 designed) and inserts the reminder notifications. Push follows
through the webhook.

### Client

- A Supabase client (URL + publishable key, both public) is provided once through Koin and
  replaces `Constants.apiBaseUrl`. Per-platform configuration replaces the localhost constants; the
  web build reads it from the page as it does today.
- Replaced: `AuthRemoteApiImpl`, `TokenRefresh`, `SessionRefresher`, `AccessTokenSource`,
  `SpaCatalogRepositoryImpl`, `GuestBookingRepositoryImpl`, `DeskRepositoryImpl`,
  `NotificationRemoteApi`, `FeatureFlagRemoteApi`, `AppUpdateRemoteApi`.
- Kept: repository interfaces, ViewModels, screens. The swap happens behind the interfaces.
- Session persistence must keep today's behaviour that a web reload does not sign the user out.
- `shared/`: the route paths and auth DTOs become obsolete and are removed; error codes, validation
  and the booking/notification DTOs stay.
- ADR 0006 (custom refresh tokens) is superseded by a new ADR recording this move.

### Repository layout

- `supabase/` at the repo root (Supabase CLI layout). `supabase/migrations/` holds the history
  already applied remotely (V1–V12 plus the two Supabase-only migrations, with matching versions)
  followed by the new ones. From now on it is the single source of the schema.
- `supabase/functions/push/` and `supabase/functions/accounts/` for the Edge Functions,
  `supabase/tests/` for database tests.

## Testing

- **Database**: pgTAP tests run by `supabase test db` against the local Supabase stack (Docker is
  available on the dev machine). They must cover, at least:
  - RLS: anon cannot read `users`, `bookings`, `notifications`, `device_tokens`; a user reads only
    their own rows; nobody can write `users.role` directly.
  - Every RPC's rules and error codes, ported from the scenarios in `server/src/test`
    (`BookingServiceTest` and friends are the behavioural spec).
  - Every privileged RPC called by the wrong role is refused.
- **Edge Functions**: Deno tests for `push` with FCM stubbed, and for `accounts` covering every
  caller role against every requested role.
- **Client**: existing ViewModel tests keep passing; repository implementations are tested against
  fakes or the local stack.
- **End to end**: a smoke run against the real project after deploy (sign up, book, owner confirms,
  reminder row appears).

## Deployment

- Migrations and the Edge Function to project `jnlonvutjugxxxsfpeom`.
- Auth settings: email confirmation off; phone provider (Twilio Verify) left disabled until the
  owner turns the OTP flag on.
- The web bundle redeployed to Cloudflare with the Supabase URL and publishable key in the page.
- CI: `./gradlew jvmTest :server:test` becomes `./gradlew jvmTest` plus `supabase test db`.

## Risks

- **A `security definer` function with a missing check is a privilege escalation.** Mitigated by
  the wrong-role tests above.
- `supabase-kt` with Ktor 3.6.0 is unverified.
- Changing every client data layer at once is a large change; the Kotlin server stays in the repo
  until the new path passes its tests, so there is something to compare against.
- Errors move from HTTP status codes to messages; any client branch on status is a silent bug if
  missed.
- The free project pauses after about a week of low activity.
- Phone validation is weaker than libphonenumber.

## Left to the builder

- Order of work. Suggested: identity and auth → catalogue and booking → owner and admin →
  flags and app update → notifications and push → phone OTP → admin page → remove `server/`.
- Exact function names and signatures, and which reads are views versus tables.
- How the pgTAP tests are organised.
- Where the Supabase client is wired in the module graph (`core:network` or a new `core` module).
