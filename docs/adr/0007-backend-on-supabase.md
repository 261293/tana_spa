# 7. The backend is Supabase, and the Ktor server is gone

Date: 2026-11-05

## Status

Accepted. Supersedes [0006](0006-refresh-tokens.md).

## Context

The owner wants the app to run for as little as possible, ideally nothing. The database already
lived on a free Supabase project. The backend was a Ktor server packaged as a JVM fat jar, and no
free host the owner accepted could run it:

- **Fly.io** was considered first and rejected; the specific reason is not recorded in this
  repository beyond the JVM not fitting a free tier the owner accepted.
- **Cloud Run** was declined by the owner.
- **Cloudflare Containers** needs Workers Paid; the Cloudflare account is on the free plan.
- **Supabase Compute** is a private alpha.

Nothing was in production: no users or data to preserve, and the client could change. The full
reasoning and the owner's decisions are in
[`docs/superpowers/specs/2026-11-04-supabase-backend-design.md`](../superpowers/specs/2026-11-04-supabase-backend-design.md).

## Decision

**Replace the server with Supabase's own platform**, inside the one free project:

- **Identity is Supabase Auth.** Sign-up, sign-in, refresh, sign-out and the phone-change code go
  to `/auth/v1/*`. A trigger on `auth.users` creates the `public.users` row as a `customer` and
  grants the welcome voucher; a second copies a confirmed phone across. `password_hash`,
  `refresh_tokens` and `phone_verifications` are dropped
  (`20261104161631_identity_on_supabase_auth.sql`), and with them the custom token design of 0006.
- **Every rule is a Postgres function.** The Data API stays closed (RLS on every table with no
  policies, table grants revoked from `anon` and `authenticated`); the client calls `public`
  functions at `/rest/v1/rpc/<name>`. Each is `security definer` with `set search_path = ''`, is
  executable only by the roles granted, and checks the caller and the permission itself. Failures
  are raised with the existing error codes and HTTP statuses, so the client's DTOs and error
  handling carry over.
- **Edge Functions only where SQL cannot do the job:** `push` (a database trigger posts each new
  notification to it through `pg_net`; it calls FCM HTTP v1) and `accounts` (creating an account
  for someone else needs the service role key).
- **The hourly reminder sweep is a `pg_cron` job**, not a coroutine in a server process.
- **The `/admin/` page is static** and ships with the web build, calling the same functions.
- **The Ktor `server/` module, its Flyway migrations, its Docker image and the compose setup are
  deleted.** `supabase/migrations/` is the only source of the schema.

The client keeps its own Ktor HTTP client, refresh-on-401 and refresh mutex; only the URLs and
request bodies changed (`shared/.../SupabasePaths.kt`). `supabase-kt` was not adopted.

## Consequences

- Running cost is the Supabase and Cloudflare free plans, with their limits: a free project that
  sees little activity for about a week is paused, and Edge Functions are capped per call and per
  month.
- The rules are enforced in the database and tested there: pgTAP in `supabase/tests/database/`,
  Deno tests beside each function, both run by CI's `supabase` job.
- **Feature flags lost their environment layer.** Resolution is override → global row → compiled
  default. The compiled defaults now exist twice, in `FeatureFlags.kt` and
  `private.flag_catalog()`, and so does the permission matrix (`SpaPermissions.kt` and
  `private.has_permission`); each pair has to change together.
- **No seeded accounts.** The first administrator signs up in the app and is promoted with SQL;
  every later privileged account is created from the app through `accounts`.
- **Phone verification is weaker.** Supabase Auth sends and checks the code; the per-country
  allow/block lists and the database-backed send caps of the old server are gone, replaced by
  Auth's own rate limits. The `phone_otp_verification` flag cannot stop Auth sending a code, so
  the SMS provider has to stay disabled in the project while the flag is off.
- **Sessions follow Supabase Auth's rules**, not 0006's: rotation and reuse detection are Auth's,
  and the client's single-flight refresh still matters for the same reason.
- **The apps always talk to the remote project.** Common code has no debug/release switch, so the
  local stack serves the tests, not the apps.
- **Admin send no longer reports delivery.** Push is asynchronous through the trigger, so
  `admin_send_notification` answers `delivered = 0, removed = 0`.
- None of this is live until the migrations and functions are deployed to the project.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Keep Ktor on **Fly.io** | Rejected by the owner; a JVM server did not fit the free tier the owner accepted |
| Keep Ktor on **Cloud Run** | Declined by the owner |
| Keep Ktor on **Cloudflare Containers** | Needs Workers Paid; the account is free |
| **Rewrite the server as one TypeScript Edge Function** behind the unchanged `/api/v1/...` contract | The client would only have changed its base URL, but about 6,200 lines of server would have been rewritten by hand, auth and refresh-token rotation included. Recommended by the assistant; the owner chose the Supabase-native approach instead |
| One Edge Function per module | Every client URL changes anyway, plus a cold start per module |
