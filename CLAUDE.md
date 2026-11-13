# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Kotlin Multiplatform app (Android, iOS, Desktop/JVM, Web/wasmJs) with a Compose Multiplatform UI and a Supabase backend (Supabase Auth, Postgres functions called over the Data API, two Edge Functions). Package root: `com.tana.spa`.

## Commands

```bash
./gradlew ktlintCheck            # CI gate; fix with ./gradlew ktlintFormat (rules in .editorconfig, max line 140)
./gradlew jvmTest                # exactly what CI runs for the Kotlin unit tests

# single module / single test
./gradlew :feature:Auth:jvmTest
./gradlew :feature:Auth:jvmTest --tests "com.tana.spa.feature.auth.presentation.LoginViewModelTest"

# run
./gradlew :composeApp:wasmJsBrowserDevelopmentRun  # web on :8080
./gradlew :composeApp:run                          # desktop
./gradlew :androidApp:assembleDebug
# iOS: open iosApp/ in Xcode (macOS only); CI links :composeApp:linkDebugFrameworkIosSimulatorArm64

# backend: the local Supabase stack (Docker), for the database and Edge Function tests
npx supabase start -x logflare,vector,studio       # or `devbox run supabase:start`
npx supabase test db                               # pgTAP, supabase/tests/database
npx supabase db reset                              # replay supabase/migrations from empty
deno test --allow-net --allow-env --allow-run supabase/functions   # the stack already serves the functions
```

Every command in order (first-time setup, daily loop, pre-push gate, schema changes, troubleshooting): `docs/local-development.md`. Full CI matrix and the local equivalent of each job: `docs/ci.md`. Before pushing, a change that touches `commonMain` should compile for the web target too (`:composeApp:compileKotlinWasmJs`), since it is the strictest target and CI builds it.

The apps talk to the remote Supabase project named in `Constants` (URL and publishable key, both public) unless `TANA_API_BASE_URL` and `TANA_SUPABASE_KEY` are set in the shell that starts the web dev server or the desktop app, which then talk to that project, normally the local stack (`docs/local-development.md`, section 1a). A production web bundle never takes them, and `verifyWebDistributionStripped` fails one that does; Android and iOS always use the compiled project. What lives outside SQL is configured in the Supabase project, not in env files: Auth settings, Edge Function secrets (`FCM_SERVICE_ACCOUNT_JSON`, `PUSH_WEBHOOK_SECRET`) and the Vault secrets `push_url`/`push_secret`. The backend in `supabase/` reaches the remote project only when it is deployed; see `docs/production-checklist.md`.

## Architecture

Read `ARCHITECTURE.md` and `docs/architecture.md` for the layer rules. The parts that span many files:

- **Module graph:** `composeApp` (wiring only) → `feature/*` → `core/{mvi,navigation,network,cache,ui,logging,featureflags,notifications}` → `shared`. Features never depend on each other and `core` never depends on `feature`. `composeApp` is a KMP *library*; `androidApp` is the thin installable app (AGP 9 forbids the application plugin beside KMP). The backend is `supabase/`, not a Gradle module.
- **`shared` is the client/backend contract, mirrored in SQL.** The DTOs, the error codes (`AuthErrorCodes`, `BookingErrorCodes`, `AccountErrorCodes`), `AuthValidation`, `SpaPermissions`, `BookingRules` and the flag catalog have copies in `supabase/migrations/`: each function returns JSON in a DTO's shape and raises these codes, and its parameters are named like the request DTO's fields. `SupabasePaths` holds the URLs. Change one side, then the other; pgTAP pins the SQL copy (e.g. the permission matrix in `020_identity.test.sql`).
- **Adding a feature** touches several places: new `feature/<Name>` module + `include(...)` in `settings.gradle.kts` (copy `feature/Feed/build.gradle.kts`), a Koin module added to `appModules()` in `composeApp/.../di/RegisterModules.kt`, and the `FeatureEntry` added to `FeaturesRegistry`. Full-screen routes (login/onboarding, no app bar) are listed in `FULL_SCREEN_ROUTES` in `App.kt`.
- **MVI:** screens are stateless composables `(state, effects, onEvent)`; only the `*Entry` object touches Koin/ViewModel. ViewModels extend `core/mvi/MviViewModel` and override **`handleEvent`** (not `onEvent`, which is a final wrapper that logs). One-shot outputs (navigation, snackbars) go through `sendEffect`, never state.
- **Navigation** is a minimal string-route back stack (`MutableNavigator`, `Routes`), not navigation-compose. Use `replaceAll` when Back must not return (after login/sign-up).
- **Feature flags** gate anything broken or unfinished. The catalog is `shared/.../featureflags/FeatureFlags.kt`, mirrored by `private.flag_catalog()` in SQL; state is resolved per call as **per-user override → global DB row → compiled default** (there is no environment layer), so a missing row means "no opinion", not "off". Inject `FeatureFlags` and branch on `isEnabled(flag)` in the client; gate a database function with `private.require_flag('<key>', uid)` (raises `503 feature_disabled`). Operated at `/admin/` (a static page shipped with the web app) by an account with `role = 'admin'`. `phone_otp_verification` currently ships **off**. Details: `docs/feature-flags.md`.
- **Roles:** `customer`, `staff`, `owner`, `admin`. Access is decided by **permission**: `SpaPermissions.kt`, mirrored by `private.has_permission`, which reads the role from `users` on every call. `admin` operates the system (feature flags and the app update config at `/admin/`, discount codes through `spa_admin_codes`/`spa_create_code`/`spa_disable_code`, which no screen calls yet); `owner` runs the spa (the booking queue, recording visits, adding treatments and therapists through the `spa_*` functions) and `staff` works the queue. Sign-up always creates a customer (a trigger on `auth.users`); an owner or admin creates other accounts through the `accounts` Edge Function, and nothing is seeded — the first admin is made with SQL after signing up (`update public.users set role = 'admin' where email = '…'`). The client's role gating is display only — the database refuses the call. Details: `docs/owner-role.md`.
- **Sessions and tokens:** Supabase Auth owns them. A sign-in (`/auth/v1/token`) returns a 1-hour access JWT and a rotating refresh token (`jwt_expiry`, `enable_refresh_token_rotation` in `supabase/config.toml`). `TokenRefresh` in `core:network` turns a 401 on a bearer call into one refresh and one retry; `SessionRefreshCoordinator` holds a mutex around that, because Auth treats a rotated refresh token spent again as reuse. Signing out clears locally first and calls `/auth/v1/logout` afterwards. Why the custom tokens went: `docs/adr/0007-backend-on-supabase.md` (0006 is superseded).
- **Errors as values:** repositories return sealed results / `AppResult`; `appResultOf` rethrows `CancellationException`. Don't catch `Exception` without rethrowing cancellation first.
- **Platform source sets:** every KMP module gets android/jvm/iosArm64/iosSimulatorArm64/wasmJs from `build-logic` convention plugins (`convention.kmp.multiplatform`, `convention.kmp.compose`). The web target has no SQLDelight driver, so its `KeyValueCache` is `localStorage` — a reload keeps the session, and the route rides in the URL fragment (`#booking`) because both dev server and published build serve one `index.html` from the root.
- **Backend** (`supabase/`): `migrations/` is the only source of the schema (never edit an applied one). The Data API is closed — RLS on every table with no policies, table grants revoked from `anon` and `authenticated` — so every read and write is a `public` function, `security definer` with `set search_path = ''`, executable only by the roles granted, that checks the caller itself; helpers live in the `private` schema. Failures go through `private.fail(code, status, message)`, which makes PostgREST answer that HTTP status with `{code, message}`. Edge Functions only where SQL cannot do the job: `push` and `accounts`. Tests: pgTAP in `supabase/tests/database/`, Deno beside each function. `docs/database-design.md` explains the model.
- **Notifications:** two separate things. The `notifications` table (V4) is the durable channel — a row is stored whether or not a device could be reached — and a push is a best-effort wake-up on top of it. `core:notifications` owns the permission/token/registration lifecycle and the unread badge; `feature/Notifications` is the centre; a trigger on `notifications` insert posts the row through `pg_net` to the `push` Edge Function (URL and shared secret from Vault; without them nothing is posted), which sends FCM HTTP v1, or only logs when `FCM_SERVICE_ACCOUNT_JSON` is unset. A device token is unique per *install*, so registering moves it to the signed-in account. The flag `notifications` ships **off**, and `admin_send_notification` checks it for the **recipient**, not the caller. Rules and the per-platform setup: `docs/notifications.md`, `docs/adr/0004-notifications-and-push.md`.
- **App update gate:** the function `app_update_config()` (callable without a session) serves one row (`app_update_config`, V7) holding the supported floor, the latest release and an escalation flag; `feature/AppUpdate` compares it against the installed version and shows a blocking or dismissible modal. It **fails open** everywhere — no row, no network, no store link configured, or `app_update_gate` off all mean no prompt — because it is the one feature that can lock out every install. Rules and the decision table: `docs/adr/0003-app-update-gate.md`.
- **URLs:** `Constants.apiBaseUrl()` is the Supabase project URL on every platform (the web page may override it). 8080 is the wasm dev server; the local stack's API is on 54321 (`supabase/config.toml`).

## Logging (required for new code)

Use `AppLog` from `core:logging`; no `println`/Timber, and never log passwords, tokens, OTP codes, bodies or full emails/phones. HTTP calls (`RequestTracing`), navigation and ViewModel effects are logged automatically; add business-outcome logs in ViewModels/repositories, and in Edge Functions log ids and outcomes only. New feature modules need `implementation(projects.core.logging)`. The client sends `X-Request-Id`/`X-Session-Id`; the `accounts` function answers CORS itself, so a new custom header sent from the browser must also be added to its `access-control-allow-headers`. Details: `.claude/skills/kmp-compose-multiplatform/references/logging.md`.

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

## Stating what you know (required)

Be transparent about what is verified, what is assumed and what is inferred. Never present an
uncertain claim as fact, never invent a source or a figure, and check anything that moves (a
version, a config value, a published behaviour) instead of recalling it. Equally, do not hedge
what you did verify: say what you ran and what it does not cover — "the gate passes; I did not
exercise it in the running app" rather than either extreme. Report failures with their output and
name any step you skipped. Full rules: the `calibrated-claims` skill.

## UI (required for new screens)

**Material 3 components first.** Build a screen out of Material's components (`ListItem`, `Card`,
`FilterChip`, `DatePicker`, `ModalBottomSheet`, `NavigationRail`, …) restyled through their
`colors`/`shape` parameters, and hand-draw only what Material has no component for. The component
map, the three deliberate exceptions (the pocket, the felt tray, the status pill) and the
checklist for a new screen: `docs/material-design.md`. Layout follows the window's width class
(`SpaWindow.size`, measured once at the root), never the platform: bottom bar on compact, rail on
medium, destinations in the app bar from expanded up, and every screen's body inside a `SpaPage`
that caps and centres it.

The palette, faces and shapes are locked in `DESIGN.md` (ADR 0008). Colours come from
`MaterialTheme.colorScheme` or `SpaTheme.extraColors`, text styles from
`SpaTheme.textStyles` — never a raw `Color(0x…)`, a `SpaColors` constant or an inline `fontSize` in
a composable. `SpaColors` is the palette the theme is built from, not an API for screens.
`SpaColorSchemeTest` and `SpaTextStylesTest` pin each token, including which base a text style
derives from. Sizes follow `DESIGN.md` rather than a rounded scale, and the app is
light-only by decision: `docs/adr/0002-light-only-colour-scheme.md`. Full rules (per-target constraints, the wasm canvas, breakpoints,
WCAG 2.2 checks, screen templates): `core:ui` plus the `kmp-compose-multiplatform` skill's
`references/ui-design.md`; library candidates are in its `references/libraries.md`.

**Motion** is one grammar, held in `SpaMotion`/`SpaEasing`: durations from `SpaTheme.motion` and
easing from `SpaEasing.OutExpo`/`InOut`, never a literal or Compose's default. A page section gets
`Modifier.revealOnScroll(order)` (fade and lift, once, on reaching the viewport); a scrolling
screen inside `AppScaffold` uses `rememberSpaScrollState()`/`rememberSpaLazyListState()`, which is
what lifts the top app bar. Route changes are decided once in `App.kt` — fade-through between bar
destinations (`TOP_LEVEL_ROUTES`, pinned by `RouteTransitionTest`), shared-axis X everywhere else.
Reduced motion is handled in `AppTheme`, so anything reading `SpaTheme.motion` inherits it. Rules
and the numbers: `docs/material-design.md`, `docs/adr/0005-motion-and-scroll-reveal.md`.

## Gotchas

- Line endings are mixed and have changed between checkouts: most docs and Kotlin sources are CRLF today, `ARCHITECTURE.md` and `docs/architecture.md` are LF, and new files under `supabase/` are LF. Measure before scripting an edit and preserve what is there: count carriage-return bytes in Python, because Git Bash grep reads the usual escape as a literal `r` and reports a meaningless number.
- `settings.gradle.kts` is the only authority on module paths; re-check `README.md` against it after adding a module.
- The Android namespace comes from the module *name* (`convention.kmp.multiplatform`), so two modules called the same thing in different layers (`core:notifications` and `feature:Notifications`) break the manifest merge. Set `androidLibrary { namespace = … }` in the newer one.
- Kotlin strings in Compose resources don't support `\'`.
- Several screens (Explore, Likes, NewPost, Profile) are placeholders, and `FeedRemoteApiImpl` returns stub data.

## Agent skills

### Installed skills

`.claude/skills/` is version controlled, so these apply to everyone. Project-specific:
`kmp-compose-multiplatform` (architecture, logging, **UI design rules**, **library choices**),
`self-documenting-code` (comment style), `calibrated-claims` (how to state what is known vs
inferred), `no-ai-attribution` (authorship). Vendored from upstream: `frontend-design`,
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
