# Logging in this project (Tana SPA)

Every new feature must ship with logs that let a production issue and a user's screen flow be
reconstructed. Use the existing `core:logging` module — do not add Timber/Napier/Kermit, `println`,
or a per-feature logger.

## What exists

| Piece | Where | Does |
|---|---|---|
| `AppLog.d/i/w/e(tag) { msg }` | `core/logging` | Leveled, lazy logging; every line is prefixed `[sessionId]` (new per app launch) |
| `LogRedact.email/phone` | `core/logging` | Masks personal data |
| `MviViewModel` | `core/mvi` | Logs created/cleared, effects, and event *type names* (DEBUG). Exposes `logTag` |
| `MutableNavigator` | `core/navigation` | Logs every navigate / replaceAll / back |
| `RequestTracing` | `core/network` | Installed by `withDefaults()`: adds `X-Request-Id` + `X-Session-Id`, logs `METHOD path req=… -> status in Nms` |

A feature that uses `NetworkClient` and `MviViewModel` already gets screen-flow, effect and HTTP
logging for free. Your job is the **business-level** logs in between.

## Checklist for a new feature

**Gradle:** add `implementation(projects.core.logging)` to the feature module's `build.gradle.kts`.

**ViewModel** (extends `MviViewModel`):
- Implement `handleEvent`, **not** `onEvent` (`onEvent` is a final wrapper that logs the event type).
- Use the inherited `logTag`: `AppLog.i(logTag) { "..." }`.
- Log each *outcome* of an async action at INFO (success) or WARN (expected failure: validation,
  rate limit, offline) — include the failure *reason enum*, counts, or ids.
- Log ignored actions (double-tap guard, cooldown) at DEBUG.
- Do not log per-keystroke state changes.

**Repository / data source:**
- Tag with a constant (`"FeedRepo"`). Log decisions that change app state: session cleared, token
  rejected (401), cache fallback used, local state corrected from the server.
- In `catch`, log the exception: `AppLog.w(TAG, e) { "..." }`. Never swallow with `catch (_: Exception)`.
  Rethrow `CancellationException` first (do not log it as an error).
- Do not add a second per-request log: `RequestTracing` already logs every HTTP call.

**Navigation:** nothing to add; `Navigator` logs it. If a ViewModel redirects (e.g. no session → login),
log *why* at the point of the decision.

**Backend (Supabase):**
- Database functions do not log; a refusal is an error code raised with `private.fail`, which the
  client logs through `RequestTracing` and the repository.
- Edge Functions (`supabase/functions/`): one `console.info` line per outcome with ids, counts and
  the outcome (see the `push` summary line), `console.warn` for a refused or failed call. Never the
  token, the body or an email.
- If you add a **new custom request header** the browser client sends to an Edge Function, add it
  to that function's `access-control-allow-headers`.

## Never log

Passwords, access tokens, OTP codes, request/response bodies, query strings, `Authorization` headers,
full emails or phone numbers (use `LogRedact` if you must show them; prefer the user id).
Kotlin serialization exception *messages* can quote the request body — log `cause::class.simpleName`
only. Do not log whole `Event`/`State` objects (`data class` `toString()` prints every field).

## Levels

- `DEBUG` – chatty developer detail (events, ignored taps). Dropped in release builds.
- `INFO` – flow milestones: screen/action outcomes, state changes.
- `WARN` – expected failures the user may hit, or odd-but-handled situations.
- `ERROR` – bugs and server 5xx. Always pass the throwable.

## Tracing a production issue

Ask for the session id (the 8-hex prefix on client lines). The client lines show the screens, the
ViewModel outcomes and every HTTP call with its `req=` id and status. The backend does not echo these
ids: correlate with the Supabase project's API and Edge Function logs by time and path.

## Forwarding to crash/telemetry

Replace the sink once at startup, don't scatter calls:
`AppLog.sink = LogSink { level, tag, message, t -> ... }` (forward WARN/ERROR; keep messages as-is, they are
already free of sensitive data if the rules above are followed). The default sink must still be called or
replaced deliberately so Logcat/console output is not lost.

## Tests

- Don't assert on log text in feature tests. A test that proves a *privacy* rule (e.g. a body never
  reaches the log) is worth having: swap `AppLog.sink` for a capturing sink and restore it in `@AfterTest`
  (see `core/logging/.../AppLogTest.kt`).
