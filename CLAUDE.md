# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Kotlin Multiplatform app (Android, iOS, Desktop/JVM, Web/wasmJs) with a Compose Multiplatform UI and a Ktor + PostgreSQL backend. Package root: `com.tana.spa`.

## Commands

```bash
./gradlew ktlintCheck            # CI gate; fix with ./gradlew ktlintFormat (rules in .editorconfig, max line 140)
./gradlew jvmTest :server:test   # exactly what CI runs for unit tests (server tests use embedded PostgreSQL, no Docker needed)

# single module / single test
./gradlew :feature:Auth:jvmTest
./gradlew :feature:Auth:jvmTest --tests "com.tana.spa.feature.auth.presentation.LoginViewModelTest"
./gradlew :server:test --tests "com.tana.spa.RequestTracingTest"

# run
./gradlew :server:run                              # needs env, see below; default port 8081 (Constants.SERVER_PORT)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun  # web on :8080
./gradlew :composeApp:run                          # desktop
./gradlew :androidApp:assembleDebug
# iOS: open iosApp/ in Xcode (macOS only); CI links :composeApp:linkDebugFrameworkIosSimulatorArm64

# local database (Postgres 17): copy .env.example to .env first
docker compose up -d db          # or `devbox run db:up`
```

Full CI matrix and the local equivalent of each job: `docs/ci.md`. Before pushing, a change that touches `commonMain` should compile for the web target too (`:composeApp:compileKotlinWasmJs`), since it is the strictest target and CI builds it.

Server config is env-driven (`AuthConfig`, `OtpConfig`, `DatabaseConfig`): `DATABASE_URL` (absent → in-memory users, accounts lost on restart), `JWT_SECRET`, `APP_ENV=production` (refuses in-memory store, dev OTP provider and random JWT secret), `TWILIO_*` (absent → dev OTP provider that prints codes to the server log). See `docs/phone-verification.md`.

## Architecture

Read `ARCHITECTURE.md` and `docs/architecture.md` for the layer rules. The parts that span many files:

- **Module graph:** `composeApp` (wiring only) → `feature/*` → `core/{mvi,navigation,network,cache,ui,logging}` → `shared`. Features never depend on each other and `core` never depends on `feature`. `composeApp` is a KMP *library*; `androidApp` is the thin installable app (AGP 9 forbids the application plugin beside KMP). `server` depends only on `shared`.
- **`shared` is the client/server contract.** `shared/.../auth/AuthContract.kt` (`AuthApi` paths, DTOs, `AuthErrorCodes`) and `AuthValidation` are compiled into both the app and the server. Change an endpoint or error code there, then update both sides.
- **Adding a feature** touches several places: new `feature/<Name>` module + `include(...)` in `settings.gradle.kts` (copy `feature/Feed/build.gradle.kts`), a Koin module added to `appModules()` in `composeApp/.../di/RegisterModules.kt`, and the `FeatureEntry` added to `FeaturesRegistry`. Full-screen routes (login/onboarding, no app bar) are listed in `FULL_SCREEN_ROUTES` in `App.kt`.
- **MVI:** screens are stateless composables `(state, effects, onEvent)`; only the `*Entry` object touches Koin/ViewModel. ViewModels extend `core/mvi/MviViewModel` and override **`handleEvent`** (not `onEvent`, which is a final wrapper that logs). One-shot outputs (navigation, snackbars) go through `sendEffect`, never state.
- **Navigation** is a minimal string-route back stack (`MutableNavigator`, `Routes`), not navigation-compose. Use `replaceAll` when Back must not return (after login/sign-up).
- **Errors as values:** repositories return sealed results / `AppResult`; `appResultOf` rethrows `CancellationException`. Don't catch `Exception` without rethrowing cancellation first.
- **Platform source sets:** every KMP module gets android/jvm/iosArm64/iosSimulatorArm64/wasmJs from `build-logic` convention plugins (`convention.kmp.multiplatform`, `convention.kmp.compose`). The web target uses an in-memory `KeyValueCache` (no SQLDelight driver), so the session is lost on browser reload.
- **Server** (`server/`): Ktor Netty, single `authModule` installs CallId/CallLogging/CORS/RateLimit/StatusPages/JWT and the `/api/v1/auth` routes. JDBC and password hashing run on `Dispatchers.IO` inside services. Schema is Flyway migrations; `docs/database-design.md` explains the model.
- **Dev URLs:** `Constants.apiBaseUrl` points Android at `10.0.2.2:8081` and everything else at `localhost:8081`. README still says the server is on 8080; the code uses 8081 (8080 is the wasm dev server).

## Logging (required for new code)

Use `AppLog` from `core:logging`; no `println`/Timber, and never log passwords, tokens, OTP codes, bodies or full emails/phones. HTTP calls (`RequestTracing`), navigation and ViewModel effects are logged automatically; add business-outcome logs in ViewModels/repositories and SLF4J logs in server routes. New feature modules need `implementation(projects.core.logging)`. The client sends `X-Request-Id`/`X-Session-Id` and the server puts them in every log line (`[req=… sid=…]`); a new custom header sent from the browser must also be added to the CORS allow-list. Details: `.claude/skills/kmp-compose-multiplatform/references/logging.md`.

## Code style (required for new code)

Names carry the meaning; comments do not. Don't narrate code — if a function needs a comment to
break it into stages, extract those stages into named functions instead. Write a comment **only**
for logic that is genuinely hard: a security or ordering constraint, a non-obvious reason for an
unusual choice, a protocol/domain fact not visible locally, an atomicity guarantee, or a
workaround. A one-line KDoc is allowed on `shared/` public API where the signature cannot say it
(units, what null means, an invariant). Full rules and examples: the `self-documenting-code`
skill. This overrides the heavier comment style in older files; don't copy it.

## Gotchas

- Files are edited on Windows: `settings.gradle.kts` uses CRLF while most sources are LF. Preserve each file's line endings when scripting edits.
- `README.md` module list is partly stale (module paths are lowercase `core/mvi`, etc.); trust `settings.gradle.kts`.
- Kotlin strings in Compose resources don't support `\'`.
- Several screens (Explore, Likes, NewPost, Profile) are placeholders, and `FeedRemoteApiImpl` returns stub data.

## Agent skills

### Issue tracker

GitHub Issues (`261293/tana_spa`, via the `gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
