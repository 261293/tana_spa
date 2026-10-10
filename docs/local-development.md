# Local development: every command you need

Three sections: **[first run](#1-first-run-new-to-this-project)** (once, when you join),
**[daily loop](#2-daily-loop)** (every day), **[before you push](#3-before-you-push)** (after you
implement anything). Then the cases that bite: **[database changes](#4-when-you-change-the-database)**,
**[contract changes](#5-when-you-change-the-clientserver-contract)**,
**[new feature module](#6-when-you-add-a-feature-module)**,
**[running the server in a container](#7-running-the-server-as-a-container)** and
**[troubleshooting](#8-when-something-does-not-work)**.

All commands run from the repository root. On Windows use Git Bash or PowerShell; the Gradle
wrapper is the same either way (`./gradlew` / `.\gradlew.bat`).

---

## 1. First run (new to this project)

### Install

| Tool | Why | Check |
|---|---|---|
| **JDK 21** (any vendor) | the whole build | `java -version` |
| **Docker Desktop** | the local PostgreSQL | `docker --version` |
| Android Studio + SDK | only for the Android app | — |
| Xcode (macOS only) | only for the iOS app | — |
| [devbox](https://www.jetify.com/devbox) *(optional)* | installs the above and gives you shortcuts | `devbox version` |

Gradle **cannot** download a JDK in this setup, so 21 has to be on your machine (or selected in
your IDE) before anything else works.

### Set up

```bash
git clone https://github.com/261293/tana_spa.git
cd tana_spa

cp .env.example .env              # never commit .env; the defaults are fine for local work
docker compose up -d --wait db    # PostgreSQL 17 on 127.0.0.1:5432
```

With devbox the first two lines are done for you (`devbox shell` copies `.env` and exports it),
and `devbox run db:up` replaces the compose command.

### Start the backend

```bash
./gradlew :server:run              # http://localhost:8081  — Flyway migrates on startup
```

The server is on **8081**. Port 8080 belongs to the web dev server. Leave this running in its own
terminal; `Ctrl+C` stops it.

> **Gradle does not read `.env`.** Only the devbox shell loads it. In a plain terminal the server
> starts with no `DATABASE_URL`, logs `store=in-memory`, and loses every account on restart — the
> usual "where did my data go?". Export the file first, or use devbox:
>
> ```bash
> set -a; . ./.env; set +a      # Git Bash / zsh — then ./gradlew :server:run
> devbox run server             # devbox does this for you
> ```
>
> PowerShell:
> ```powershell
> Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
>   $kv = $_ -split '=', 2; [Environment]::SetEnvironmentVariable($kv[0].Trim(), $kv[1].Trim())
> }
> ```

### Check it actually works

```bash
curl http://localhost:8081/                           # Hello, Ktor!
curl http://localhost:8081/api/v1/feature-flags       # {"flags":{"phone_otp_verification":false}}
```

Then open **<http://localhost:8081/admin/>** and sign in with `sanghtruong@tana.local` /
`P@ssw0rd26` (created automatically on every start-up — see
[feature-flags.md](feature-flags.md)). You should see the flag table.

In the startup log you should find:

```
Created administrator sanghtruong@tana.local (…)
Starting server on port 8081 (env=unset, store=postgres)
Feature flag deploy defaults: phone_otp_verification=off
```

`store=in-memory` instead of `postgres` means `DATABASE_URL` is not set — the server still runs,
but every account is lost on restart.

### Start a client

Pick whichever you are working on; each one talks to the server above.

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web, http://localhost:8080
./gradlew :composeApp:run                           # desktop
./gradlew :androidApp:assembleDebug                 # Android APK (emulator reaches the host at 10.0.2.2)
# iOS: open iosApp/ in Xcode and run (macOS only)
```

### Prove the round trip

Sign up in the app. With `phone_otp_verification` off (the default) you land on the welcome
screen and the account exists with `phoneVerified: false`. Turn the flag on in the portal, sign up
again, and you get the verify-phone screen instead — the code is printed in the **server log**
(the dev OTP provider never sends a real SMS).

---

## 2. Daily loop

```bash
docker compose up -d --wait db     # or: devbox run db:up

# terminal 1 — the env export is what connects it to PostgreSQL (see the note above)
set -a; . ./.env; set +a; ./gradlew :server:run     # or just: devbox run server

# terminal 2
./gradlew :composeApp:run          # or wasmJsBrowserDevelopmentRun / assembleDebug
```

Database housekeeping:

```bash
devbox run db:psql                 # psql shell, or:
docker compose exec db psql -U tana tana_spa

docker compose down                # stop, keep the data
docker compose down -v             # stop and DELETE the data (replays every migration next time)
devbox run db:reset                # the same two steps in one
```

Running one test while you work:

```bash
./gradlew :server:test --tests "com.tana.spa.featureflags.FeatureFlagRoutesTest"
./gradlew :feature:Auth:jvmTest --tests "com.tana.spa.feature.auth.presentation.LoginViewModelTest"
```

Server tests use an **embedded** PostgreSQL, so they need no Docker and do not touch your dev
database.

---

## 3. Before you push

This is exactly what CI runs ([ci.md](ci.md)), in the cheapest-first order. Run all of it after
implementing anything.

```bash
# 1. formatting — fixes most style failures for you
./gradlew ktlintFormat
./gradlew ktlintCheck

# 2. tests: shared code, every feature module, and the backend
./gradlew jvmTest :server:test

# 3. the web target is the strictest compiler; anything touching commonMain must pass it
./gradlew :composeApp:compileKotlinWasmJs

# 4. the shipped artifacts still build
./gradlew :composeApp:verifyWebDistributionStripped :androidApp:assembleDebug :server:assemble

# 5. no secret in your commits
gitleaks git --redact -v .
```

On a Mac, also `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`.

Read the last line of Gradle's own output (`BUILD SUCCESSFUL` / `BUILD FAILED`). If you pipe the
command through `grep`, the exit code you see is **grep's**, not Gradle's — a failed build can
look green.

---

## 4. When you change the database

Flyway runs on server start-up, so "deploying" a schema change locally is just restarting the
server. Two things are easy to forget:

```bash
# 1. add a NEW file; never edit one that has already been applied (checksums will fail)
#    server/src/main/resources/db/migration/V7__your_change.sql

# 2. two tests assert how many migrations exist — bump BOTH:
#    server/src/test/kotlin/com/tana/spa/db/TestDatabase.kt   -> migrationsExecuted == N
#    server/src/test/kotlin/com/tana/spa/db/DatabaseTest.kt    -> assertEquals(N, applied)  (x2)

devbox run db:reset                # replay from empty, so you test what a fresh deploy gets
./gradlew :server:run              # applies it
./gradlew :server:test             # the embedded-PostgreSQL tests run every migration too
```

Set `DATABASE_MIGRATE=false` in `.env` only if you want to run migrations as a separate deploy
step; the server then starts without touching the schema.

Conventions (ids are `uuid`, money is `bigint` VND, timestamps are `timestamptz`, enum-like
columns are `text` + `CHECK`) and the current model: [database-design.md](database-design.md).

---

## 5. When you change the client/server contract

`shared/` is compiled into **both** the apps and the server, so an endpoint, DTO or error code
lives in one place and both sides must be updated together:

- `shared/.../auth/AuthContract.kt` — paths, DTOs, `AuthErrorCodes`
- `shared/.../featureflags/` — the flag catalog and the flag API
- `shared/.../admin/AdminContract.kt` — the admin portal's endpoints

After editing it: `./gradlew :composeApp:compileKotlinWasmJs :server:compileKotlin`. A new custom
HTTP header sent from the browser must also be added to the CORS allow-list in
`server/.../auth/AuthModule.kt`, or the web client's preflight fails.

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

## 7. Running the server as a container

The compose `server` service runs the jar Gradle builds, so build it first:

```bash
./gradlew :server:buildFatJar
docker compose --profile app up -d --build     # or: devbox run docker:up
docker compose logs -f server
docker compose --profile app down
```

For a real deployment, the server is configured entirely by environment variables:

| Variable | Needed when |
|---|---|
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | always — **required** once `APP_ENV=production` |
| `JWT_SECRET` (32+ random chars: `openssl rand -base64 48`) | always — required in production, otherwise every restart logs users out |
| `APP_ENV=production` | refuses to start with the in-memory store, the dev OTP provider or a random JWT secret |
| `CORS_ALLOWED_ORIGINS` | the web client's origin |
| `TRUST_PROXY=true` | **only** behind a proxy that sets `X-Forwarded-For` |
| `TWILIO_VERIFY_SERVICE_SID`, `TWILIO_API_KEY_SID`, `TWILIO_API_KEY_SECRET` | real SMS; required in production |
| `FEATURE_<KEY>` | the flag state this deployment *starts* from |

Feature flags do **not** need a deploy: `FEATURE_<KEY>` is only the fallback, and whatever an
administrator stores through `/admin/` wins over it, globally or per user. Change the seeded admin
passwords (`server/.../auth/AdminSeeder.kt`) before the server is reachable by anyone you do not
trust — they are public in this repository.

Release builds of the clients: see the [README](../README.md#release-builds).

---

## 8. When something does not work

| Symptom | Cause and fix |
|---|---|
| `Could not find a JDK` / toolchain error | JDK 21 is not installed and Gradle cannot provision one here. Install it, or point your IDE at it. |
| `Address already in use` on 8081 | another `:server:run` is still going. Stop it, or set `PORT` in `.env`. |
| `DATABASE_URL must be set when APP_ENV=production` | you set `APP_ENV=production` locally. Use `development`. |
| `store=in-memory` in the log, accounts vanish on restart | `DATABASE_URL` is not in the server's environment. Gradle does not read `.env`: `set -a; . ./.env; set +a` first, or use `devbox run server`. Then check the database is up. |
| `Validate failed: Migration checksum mismatch` | an applied migration was edited. `devbox run db:reset`, and add a new `V<N>__…sql` instead. |
| `expected N migrations, ran M` | you added a migration without bumping the two test counts — see [§4](#4-when-you-change-the-database). |
| Android app cannot reach the server | the emulator's host is `10.0.2.2`, already handled by `Constants.apiBaseUrl`; make sure the server is running on the host, not inside the emulator. |
| Web client blocked by CORS | add its origin to `CORS_ALLOWED_ORIGINS`, and any new custom header to the CORS allow-list. |
| OTP screen never appears | `phone_otp_verification` is off by default. Turn it on at `/admin/`. |
| No SMS arrives | expected: without `TWILIO_*` the dev provider prints the code to the **server log**. |
| Cannot sign in to `/admin/` | the seeded accounts only exist if their email was free. Fall back to SQL: `UPDATE feature_flags SET enabled = true WHERE key = '…';` |
| ktlint fails on formatting | `./gradlew ktlintFormat`. In `.gradle.kts` files use `//`, not `/** */`. |
| `Unclosed comment` from the Kotlin compiler | a `/*` inside a KDoc (e.g. writing a route as `otp/*`) — Kotlin block comments nest. |
| wasm build fails on `yarn.lock` | commit the updated `kotlin-js-store/wasm/yarn.lock`. |
