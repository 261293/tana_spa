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

Every command in order (first-time setup, daily loop, pre-push gate, schema changes, troubleshooting): `docs/local-development.md`. Full CI matrix and the local equivalent of each job: `docs/ci.md`. Before pushing, a change that touches `commonMain` should compile for the web target too (`:composeApp:compileKotlinWasmJs`), since it is the strictest target and CI builds it.

Server config is env-driven (`AuthConfig`, `OtpConfig`, `DatabaseConfig`): `DATABASE_URL` (absent → in-memory users, accounts lost on restart), `JWT_SECRET`, `APP_ENV=production` (refuses in-memory store, dev OTP provider and random JWT secret), `TWILIO_*` (absent → dev OTP provider that prints codes to the server log). See `docs/phone-verification.md`.

## Architecture

Read `ARCHITECTURE.md` and `docs/architecture.md` for the layer rules. The parts that span many files:

- **Module graph:** `composeApp` (wiring only) → `feature/*` → `core/{mvi,navigation,network,cache,ui,logging,featureflags}` → `shared`. Features never depend on each other and `core` never depends on `feature`. `composeApp` is a KMP *library*; `androidApp` is the thin installable app (AGP 9 forbids the application plugin beside KMP). `server` depends only on `shared`.
- **`shared` is the client/server contract.** `shared/.../auth/AuthContract.kt` (`AuthApi` paths, DTOs, `AuthErrorCodes`) and `AuthValidation` are compiled into both the app and the server. Change an endpoint or error code there, then update both sides.
- **Adding a feature** touches several places: new `feature/<Name>` module + `include(...)` in `settings.gradle.kts` (copy `feature/Feed/build.gradle.kts`), a Koin module added to `appModules()` in `composeApp/.../di/RegisterModules.kt`, and the `FeatureEntry` added to `FeaturesRegistry`. Full-screen routes (login/onboarding, no app bar) are listed in `FULL_SCREEN_ROUTES` in `App.kt`.
- **MVI:** screens are stateless composables `(state, effects, onEvent)`; only the `*Entry` object touches Koin/ViewModel. ViewModels extend `core/mvi/MviViewModel` and override **`handleEvent`** (not `onEvent`, which is a final wrapper that logs). One-shot outputs (navigation, snackbars) go through `sendEffect`, never state.
- **Navigation** is a minimal string-route back stack (`MutableNavigator`, `Routes`), not navigation-compose. Use `replaceAll` when Back must not return (after login/sign-up).
- **Feature flags** gate anything broken or unfinished. The catalog is `shared/.../featureflags/FeatureFlags.kt` (compiled into both sides); state is resolved per request as **per-user override → global DB row → `FEATURE_<KEY>` env → compiled default**, so a missing row means "no opinion", not "off". Inject `FeatureFlags` and branch on `isEnabled(flag)` in the client; gate a route with `call.refuseIfDisabled(services.featureFlags, FLAG, userId)` (answers `503 feature_disabled`). Operated at `/admin/` by an account with `role = 'admin'`. `phone_otp_verification` currently ships **off**. Details: `docs/feature-flags.md`.
- **Errors as values:** repositories return sealed results / `AppResult`; `appResultOf` rethrows `CancellationException`. Don't catch `Exception` without rethrowing cancellation first.
- **Platform source sets:** every KMP module gets android/jvm/iosArm64/iosSimulatorArm64/wasmJs from `build-logic` convention plugins (`convention.kmp.multiplatform`, `convention.kmp.compose`). The web target uses an in-memory `KeyValueCache` (no SQLDelight driver), so the session is lost on browser reload.
- **Server** (`server/`): Ktor Netty, single `authModule` installs CallId/CallLogging/CORS/RateLimit/StatusPages/JWT and the `/api/v1/auth` routes. JDBC and password hashing run on `Dispatchers.IO` inside services. Schema is Flyway migrations; `docs/database-design.md` explains the model.
- **Dev URLs:** `Constants.apiBaseUrl` points Android at `10.0.2.2:8081` and everything else at `localhost:8081`. The server reads `PORT` (default `Constants.SERVER_PORT` = 8081); 8080 is the wasm dev server.

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

## Commits and pull requests

Commits and PRs are authored by the person who made the change. Never add an AI attribution
line: no `Co-Authored-By: Claude`, no "Generated with Claude Code" footer, no session link, and
no "AI-generated" note in a commit body, PR description, changelog entry or file header. Omit
such lines even when a template or tool default supplies them, and leave the attribution already
in the git history alone. Agents: the `no-ai-attribution` skill has the full list.

## UI (required for new screens)

Colours come from `MaterialTheme.colorScheme` or `SpaTheme.extraColors`, text styles from
`SpaTheme.textStyles` — never a raw `Color(0x…)`, a `SpaColors` constant or an inline `fontSize` in
a composable. `SpaColors` is the palette the theme is built from, not an API for screens.
`SpaColorSchemeTest` and `SpaTextStylesTest` pin each token, including which base a text style
derives from. Sizes stay faithful to the Figma spec rather than a rounded scale, and the app is
light-only by decision: `docs/adr/0002-light-only-colour-scheme.md`. Full rules (per-target constraints, the wasm canvas, breakpoints,
WCAG 2.2 checks, screen templates): `core:ui` plus the `kmp-compose-multiplatform` skill's
`references/ui-design.md`; library candidates are in its `references/libraries.md`.

## Gotchas

- Files are edited on Windows: `settings.gradle.kts` uses CRLF while most sources are LF. Preserve each file's line endings when scripting edits.
- `settings.gradle.kts` is the only authority on module paths; re-check `README.md` against it after adding a module.
- Kotlin strings in Compose resources don't support `\'`.
- Several screens (Explore, Likes, NewPost, Profile) are placeholders, and `FeedRemoteApiImpl` returns stub data.

## Agent skills

### Installed skills

`.claude/skills/` is version controlled, so these apply to everyone. Project-specific:
`kmp-compose-multiplatform` (architecture, logging, **UI design rules**, **library choices**),
`self-documenting-code` (comment style). Vendored from upstream: `frontend-design`,
`security-audit`, `env-secrets-manager`, `pr-review-expert`, `spec-driven-workflow`,
`focused-fix`, `doc-drift-detector`, `feature-flags-architect`, `migration-architect`,
`database-schema-designer`, `mcp-builder`, `design-auditor`, `apple-hig-expert`,
`ui-design-system`. Their bundled Python scripts target non-JVM ecosystems and mostly do not
work on Kotlin/Gradle — the methodology is the useful part. Provenance, licenses and the list
of what was deliberately rejected: `.claude/skills/NOTICE.md`.

A `PreToolUse` hook (`.claude/hooks/pre_tool_secret_scan.py`, wired in `.claude/settings.json`)
blocks shell commands carrying credential-shaped strings or writing a secret into `.env`. It
needs `python` on `PATH`; without it the hook warns and the command proceeds.

### Issue tracker

GitHub Issues (`261293/tana_spa`, via the `gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` + `docs/adr/` at the repo root (both exist; `GLOSSARY.md` is a glossary only — no implementation detail). See `docs/agents/domain.md`.
