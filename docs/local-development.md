# Local development: every command you need

Three sections: **[first run](#1-first-run-new-to-this-project)** (once, when you join),
**[daily loop](#2-daily-loop)** (every day), **[before you push](#3-before-you-push)** (after you
implement anything). Then the cases that bite: **[database changes](#4-when-you-change-the-database)**,
**[contract changes](#5-when-you-change-the-clientbackend-contract)**,
**[new feature module](#6-when-you-add-a-feature-module)**,
**[Edge Functions](#7-edge-functions)** and
**[troubleshooting](#8-when-something-does-not-work)**.

All commands run from the repository root. On Windows use Git Bash or PowerShell; the Gradle
wrapper is the same either way (`./gradlew` / `.\gradlew.bat`).

---

## 1. First run (new to this project)

### Install

| Tool | Why | Check |
|---|---|---|
| **JDK 21** (any vendor) | the whole Kotlin build | `java -version` |
| **Node.js** (22 in CI and devbox) | runs the Supabase CLI through `npx` | `node --version` |
| **Docker Desktop** | the local Supabase stack | `docker --version` |
| [Deno](https://deno.com) (2.9.8 in CI) | only for the Edge Function tests | `deno --version` |
| Android Studio + SDK | only for the Android app | — |
| Xcode (macOS only) | only for the iOS app | — |
| [devbox](https://www.jetify.com/devbox) *(optional)* | installs JDK and Node and gives you shortcuts | `devbox version` |

Gradle **cannot** download a JDK in this setup, so 21 has to be on your machine (or selected in
your IDE) before anything else works.

### Where the apps get their data

The apps always talk to the remote Supabase project named in `shared/.../Constants.kt`
(`SUPABASE_URL` and its publishable key, both public by design). There is no debug build that
points at your machine: common code has no debug/release switch. The local stack below is for the
database and Edge Function tests.

The backend in `supabase/` reaches the remote project only when it is deployed. Until then an app
built from this branch calls functions the remote project may not have yet.

### Start the local stack

```bash
git clone https://github.com/261293/tana_spa.git
cd tana_spa

npx supabase start -x logflare,vector,studio    # or: devbox run supabase:start
npx supabase status                             # URLs and the local keys
```

The first start pulls the Docker images and applies every file in `supabase/migrations/`. The API
gateway is on **54321** and the database on **54322** (`supabase/config.toml`). The local keys are
the CLI's well-known development keys, not secrets.

### Check it actually works

```bash
npx supabase test db     # every pgTAP file in supabase/tests/database ends with "Result: PASS"
```

### Start a client

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web, http://localhost:8080
./gradlew :composeApp:run                           # desktop
./gradlew :androidApp:assembleDebug                 # Android APK
# iOS: open iosApp/ in Xcode and run (macOS only)
```

The admin page (feature flags, app update config) is `/admin/` of the web build:
`composeApp/src/webMain/resources/admin/index.html`. It signs in with an account whose `role` is
`admin`; nothing is seeded, so make one by signing up and then, in SQL,
`update public.users set role = 'admin' where email = '…';`.

---

## 2. Daily loop

```bash
npx supabase start -x logflare,vector,studio    # no-op if it is already running

./gradlew :composeApp:run                       # or wasmJsBrowserDevelopmentRun / assembleDebug
```

Database housekeeping:

```bash
npx supabase db reset           # drop the local database and replay every migration (devbox run supabase:reset)
npx supabase stop               # stop the stack (devbox run supabase:stop)
```

Connect with any Postgres client to `postgresql://postgres:postgres@127.0.0.1:54322/postgres`
(the local default printed by `npx supabase status`).

Running one test while you work:

```bash
./gradlew :feature:Auth:jvmTest --tests "com.tana.spa.feature.auth.presentation.LoginViewModelTest"
npx supabase test db supabase/tests/database/060_owner_desk.test.sql
```

---

## 3. Before you push

This is what CI runs ([ci.md](ci.md)), in the cheapest-first order. Run all of it after
implementing anything.

```bash
# 1. formatting — fixes most style failures for you
./gradlew ktlintFormat
./gradlew ktlintCheck

# 2. Kotlin tests: shared code and every module
./gradlew jvmTest

# 3. the web target is the strictest compiler; anything touching commonMain must pass it
./gradlew :composeApp:compileKotlinWasmJs

# 4. the shipped artifacts still build
./gradlew :composeApp:verifyWebDistributionStripped :androidApp:assembleDebug

# 5. the backend (needs the local stack): database tests, then the Edge Function tests
npx supabase test db
deno test --allow-net --allow-env --allow-run supabase/functions

# 6. no secret in your commits
gitleaks git --redact -v .
```

On a Mac, also `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`.

Read the last line of Gradle's own output (`BUILD SUCCESSFUL` / `BUILD FAILED`). If you pipe the
command through `grep`, the exit code you see is **grep's**, not Gradle's — a failed build can
look green.

---

## 4. When you change the database

`supabase/migrations/` is the only source of the schema. Two things are easy to forget:

```bash
# 1. add a NEW file; never edit one that has already been applied
npx supabase migration new your_change          # creates supabase/migrations/<timestamp>_your_change.sql

# 2. replay from empty, so you test what a fresh deploy gets, then run the tests
npx supabase db reset
npx supabase test db
```

The house rules every migration follows:

- a new table gets `alter table … enable row level security` and **no policy**: nothing reads a
  table through the Data API;
- a new `public` function is `security definer` with `set search_path = ''`, names every object
  with its schema, is revoked from `public` and granted to the roles that may call it (`anon` only
  for what a signed-out guest needs), and checks the caller itself (`private.require_permission`);
- a failure is `perform private.fail('<code>', <status>, '<message>')`, with a code from
  `shared/` (`AuthErrorCodes`, `BookingErrorCodes`, `AccountErrorCodes`);
- each rule gets a pgTAP test in `supabase/tests/database/`, including the wrong role being refused.

Conventions (ids are `uuid`, money is whole dong in `*_vnd` columns, timestamps are
`timestamptz`, enum-like columns are `text` + `CHECK`) and the current model:
[database-design.md](database-design.md).

---

## 5. When you change the client/backend contract

`shared/` holds what the app sends and decodes, and the SQL mirrors it. Change both together:

- DTOs and error codes — `shared/.../auth/`, `booking/`, `notifications/`, `appupdate/`; a
  function's parameters are named like the request DTO's fields and it returns JSON in the
  response DTO's shape;
- `SpaPermissions.kt` — mirrored by `private.has_permission`, pinned by
  `supabase/tests/database/020_identity.test.sql`;
- `FeatureFlags.kt` — mirrored by `private.flag_catalog()`;
- `SupabasePaths.kt` — the Auth, RPC and function URLs.

After editing: `./gradlew :composeApp:compileKotlinWasmJs` and `npx supabase test db`. A new
custom HTTP header sent from the browser must also be allowed by the `accounts` function's
`access-control-allow-headers` (`supabase/functions/accounts/index.ts`), or its preflight fails.

---

## 6. When you add a feature module

Four places, or the screen will not appear:

1. `feature/<Name>/build.gradle.kts` — copy `feature/Feed/build.gradle.kts`
2. `include(":feature:<Name>")` in `settings.gradle.kts` *(this file is CRLF — keep it that way)*
3. its Koin module added to `appModules()` in `composeApp/.../di/RegisterModules.kt`
4. its `FeatureEntry` added to `FeaturesRegistry`, and the route added to `FULL_SCREEN_ROUTES`
   in `App.kt` if it has no app bar

New modules need `implementation(projects.core.logging)` (use `AppLog`, never `println`), and
`implementation(projects.core.featureflags)` if the feature is gated by a flag.

---

## 7. Edge Functions

Two functions live in `supabase/functions/`, each with its Deno tests beside it:

- `push` — called by the database for each new notification; sends it through FCM;
- `accounts` — creates an account for an owner or admin.

`npx supabase start` already runs an edge runtime that serves both, at
`http://127.0.0.1:54321/functions/v1/<name>`; the tests need nothing else:

```bash
deno test --allow-net --allow-env --allow-run supabase/functions   # unit tests, plus accounts' local_stack test
```

The `local_stack` test in `accounts/index_test.ts` creates and deletes its own users on the local
stack and calls the `accounts` function through the gateway; it fails, rather than skips, when
the stack is down. It reads the local keys from `npx supabase status -o env`.

To run a function with secrets (`PUSH_WEBHOOK_SECRET`, `FCM_SERVICE_ACCOUNT_JSON`), serve it with
`npx supabase functions serve`, which reads them from `supabase/functions/.env` (gitignored).
Without them `push` refuses every call (no webhook secret) or only logs (no FCM key).

Release builds of the clients: see the [README](../README.md#release-builds).

---

## 8. When something does not work

| Symptom | Cause and fix |
|---|---|
| `Could not find a JDK` / toolchain error | JDK 21 is not installed and Gradle cannot provision one here. Install it, or point your IDE at it. |
| `npx supabase start` fails | Docker is not running, or a port in `supabase/config.toml` (54321–54324) is taken. `npx supabase stop` and start again. |
| `npx supabase test db` fails after pulling | the local database is behind the migrations. `npx supabase db reset`. |
| The first call to a function answers `502` | the edge runtime is still fetching the function's dependencies; retry after a few seconds. |
| `local_stack` fails with "the local stack is not running" | start the stack: `npx supabase start -x logflare,vector,studio`. |
| The app shows errors for every call | the remote project does not have this branch's functions yet; they arrive with the deployment. |
| OTP screen never appears | `phone_otp_verification` is off by default. Turn it on at `/admin/` — and enable the phone provider in the Supabase project ([phone-verification.md](phone-verification.md)). |
| Cannot sign in to `/admin/` | no account has `role = 'admin'`. Promote one with SQL: `update public.users set role = 'admin' where email = '…';` |
| ktlint fails on formatting | `./gradlew ktlintFormat`. In `.gradle.kts` files use `//`, not `/** */`. |
| `Unclosed comment` from the Kotlin compiler | a `/*` inside a KDoc (e.g. writing a route as `otp/*`) — Kotlin block comments nest. |
| wasm build fails on `yarn.lock` | commit the updated `kotlin-js-store/wasm/yarn.lock`. |
