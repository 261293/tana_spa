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
| Server `CallId` + `CallLogging` | `server/.../auth/AuthModule.kt` | Echoes the request id and puts `[req=… sid=…]` on every server log line |

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

**Server (Ktor):**
- `private val log = LoggerFactory.getLogger("com.tana.spa.<area>")`; SLF4J placeholders (`{}`), not string templates.
- One INFO line per route result: `log.info("thing -> {}", result::class.simpleName)` plus the user id
  when known. Log which rule refused a request (cooldown / cap / claimed), at WARN if it looks like abuse.
- Errors: `log.error("...", cause)` — the request id is already in the line via MDC.
- New route plugin/handler needs no id plumbing: `CallId` + MDC are installed once in `authModule`.
  If you add a **new custom request header** the browser client sends, add it to CORS `allowHeader`.

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

Ask for the session id (the 8-hex prefix on client lines). `grep "sid=<id>"` on the server log gives that
user's whole journey; `grep "req=<id>"` gives one HTTP call end to end. Client lines then show the screens
and ViewModel outcomes around it.

## Forwarding to crash/telemetry

Replace the sink once at startup, don't scatter calls:
`AppLog.sink = LogSink { level, tag, message, t -> ... }` (forward WARN/ERROR; keep messages as-is, they are
already free of sensitive data if the rules above are followed). The default sink must still be called or
replaced deliberately so Logcat/console output is not lost.

## Tests

- Don't assert on log text in feature tests. A test that proves a *privacy* rule (e.g. a body never
  reaches the log) is worth having: swap `AppLog.sink` for a capturing sink and restore it in `@AfterTest`
  (see `core/logging/.../AppLogTest.kt`); on the server attach a logback `ListAppender`
  (see `server/.../RequestTracingTest.kt`).
