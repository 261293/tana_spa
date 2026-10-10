# KMP library selection

Curated from [kmp-awesome](https://github.com/terrakok/kmp-awesome), filtered to what this
project could actually use. The list is a shortlist of candidates, **not** a set of approved
dependencies — nothing here is in the build yet.

## Vet before adopting

This project builds five targets: `android`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `wasmJs`.
`wasmJs` is the strictest and CI builds it, so a library that stops at Android/iOS/JVM will break
`:composeApp:compileKotlinWasmJs` and fail CI.

1. Check the library's own published targets on Maven Central — not its README, which often lags.
   A `-wasm-js` artifact variant is the only reliable signal of web support.
2. Add it to `gradle/libs.versions.toml`, never as an inline coordinate.
3. Compile the web target before pushing: `./gradlew :composeApp:compileKotlinWasmJs`.
4. Prefer a library that has no `expect/actual` gap you would have to fill per platform.

## Already chosen — do not replace without an ADR

| Need | This project uses | Notes |
|---|---|---|
| HTTP client | [Ktor](https://github.com/ktorio/ktor) | `core:network`, `RequestTracing` adds `X-Request-Id`/`X-Session-Id` |
| DI | [Koin](https://github.com/InsertKoinIO/koin) | modules registered in `appModules()` |
| Local cache | [SQLDelight](https://github.com/cashapp/sqldelight) | no wasm driver — web uses an in-memory `KeyValueCache`, so the session is lost on reload |
| Logging | `AppLog` (`core:logging`) | mandated; see `logging.md` |
| Navigation | `MutableNavigator` + `Routes` | a deliberate minimal string-route back stack, not navigation-compose |
| Testing | [Turbine](https://github.com/cashapp/turbine) | see `testing.md` |

Navigation libraries in the wild ([Voyager](https://github.com/adrielcafe/voyager),
Precompose, Appyx, Tiamat) are **not** candidates here: the minimal navigator is an intentional
choice recorded in `CLAUDE.md`. Replacing it is an architecture decision, not a library swap.

## Candidates by need

### Images — the Feed needs this first

`FeedRemoteApiImpl` returns stub data today; once it returns real posts the feed needs async
image loading with caching.

- [Coil](https://github.com/coil-kt/coil) — the default choice; Coil 3 is Compose-Multiplatform-first.
- [Kamel](https://github.com/Kamel-Media/Kamel), [Sketch](https://github.com/panpf/sketch),
  [Landscapist](https://github.com/skydoves/landscapist) — alternatives if Coil's target set or
  caching model does not fit.
- [ZoomImage](https://github.com/panpf/zoomimage) — pinch-zoom for a full-screen post view.

### Loading and empty states

- [compose-placeholder](https://github.com/eygraber/compose-placeholder) — skeleton UI while a
  feed page loads. Pairs with the existing `AppResult` loading state.

### Theming

- [MaterialKolor](https://github.com/jordond/materialkolor) — generate a full Material 3 scheme
  from one seed color, which keeps `ui-design.md`'s token rule enforceable in code.
- [kmPalette](https://github.com/jordond/kmpalette) — derive a palette from an image, e.g. tinting
  a profile header from its avatar.

### Responsive layout — desktop and web need this

- [material3-windowsizeclass-multiplatform](https://github.com/chrisbanes/material3-windowsizeclass-multiplatform)
  — window size classes outside Android. Desktop and browser windows resize freely, so the
  phone-shaped layouts need breakpoints; see `ui-design.md`.

### iOS fidelity

- [Compose Cupertino](https://github.com/alexzhirkevich/compose-cupertino) — iOS-styled components.
- [Calf](https://github.com/MohamedRejeb/Calf) — adaptive components that render per platform.

Both trade uniformity for native feel. Decide once, globally — a half-adaptive app reads as broken.
`apple-hig-expert` covers what iOS users expect before you reach for either.

### NewPost screen (placeholder today)

- [peekaboo](https://github.com/TEAM-PREAT/peekaboo) — image picker.
- [Compose Rich Editor](https://github.com/MohamedRejeb/Compose-Rich-Editor) — rich text input.
- [FlexibleBottomSheet](https://github.com/skydoves/FlexibleBottomSheet) — sheet-based composer.

### Sharing a post

- [kmp-sharing](https://github.com/software-mansion/kmp-sharing) — platform share sheets.

### Performance debugging

- [rebugger](https://github.com/theapache64/rebugger) — logs *why* a composable recomposed.
- [compose-stable-marker](https://github.com/skydoves/compose-stable-marker) — `@Stable`/`@Immutable`
  markers for KMP modules where the Compose compiler cannot infer stability. Relevant because
  state classes live in `feature/*` modules away from the UI.

Debug-only dependencies. Keep them out of release configurations.

### Not needed yet

[Vico](https://github.com/patrykandpatrick/vico) and
[Koala Plot](https://github.com/KoalaPlot/koalaplot-core) (charts),
[Compose Media Player](https://github.com/kdroidFilter/ComposeMediaPlayer) (video),
[compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform),
[Haze](https://github.com/chrisbanes/haze) (blur),
[Kottie](https://github.com/ismai117/kottie) (Lottie),
[Compose Icons](https://github.com/DevSrSouza/compose-icons) (extra icon packs).

### Logging — read before touching

`AppLog` is the required entry point and `println`/Timber are banned. These exist as *sinks*
`AppLog` could delegate to per platform, never as replacements at call sites:
[Kermit](https://github.com/touchlab/Kermit), [Napier](https://github.com/AAkira/Napier),
[KmLogging](https://github.com/LighthouseGames/KmLogging),
[kotlin-logging](https://github.com/oshai/kotlin-logging). Changing the sink must not change the
redaction rules in `logging.md`: no passwords, tokens, OTP codes, bodies or full emails/phones.
