This is a modular Kotlin Multiplatform project targeting **Android, iOS, Web (Wasm), Desktop (JVM) and a Ktor Server**, organized after the layered `core` / `feature` architecture of
[inassar0/kmp_instagram_clone](https://github.com/inassar0/kmp_instagram_clone).

## Module layout

```
build-logic/                 Gradle convention plugins (shared KMP + Compose config)
composeApp/                  Compose Multiplatform app (KMP library): App UI + all
                             non-Android entry points (iosMain, jvmMain, webMain) + Koin init
androidApp/                  Android application module (APK) that wraps composeApp
iosApp/                      Xcode project (SwiftUI host for the ComposeApp framework)
server/                      Ktor server (JVM)
shared/                      Non-UI shared code: Platform expect/actual, Greeting,
                             Constants, DI (sharedModule), helpers
core/
  Navigation/                FeatureEntry / Navigator / Routes
  Network/                   Ktor client + per-platform engine (PlatformClientEngine)
  UI/                        AppTheme, AppScaffold, NavBar, AppBars, NavConfig
  Mvi/                       MviViewModel base (State / Event / Effect)
  Cache/                     KeyValueCache abstraction (in-memory stub; SQLDelight in a full build)
feature/
  Auth, Explore, Likes,      Entry + Screen per feature
  NewPost, Profile
  Feed/                      Full clean-arch reference: data (remote/cache/repository),
                             domain (model/mapper/repository), presentation (ui + MVI viewmodel)
```

Each `core/*` and `feature/*` module and `shared` applies the `convention.kmp.multiplatform`
plugin (androidLibrary + jvm + iosArm64 + iosSimulatorArm64 + wasmJs); UI modules add
`convention.kmp.compose`. Koin wires the modules together (`composeApp` `InitKoin` /
`RegisterModules`), and every feature contributes a `FeatureEntry` collected by `FeaturesRegistry`.

> Note on AGP 9: the app module could not stay a single `composeApp` as in the reference,
> because AGP 9 forbids `com.android.application` alongside the Kotlin Multiplatform plugin.
> `composeApp` is therefore a KMP **library** holding all shared/UI code, and the thin
> `androidApp` module is the installable Android application.

## Running the apps

- Web (dev server): `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` → http://localhost:8080
- Web (production bundle): `./gradlew :composeApp:wasmJsBrowserDistribution`
- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app: `./gradlew :composeApp:run`
- Server: `./gradlew :server:run` (try http://localhost:8080/ and /demo)
- iOS app: open [/iosApp](./iosApp) in Xcode and run it (requires macOS/Xcode).

## Release builds

Release artifacts are shrunk and obfuscated so a downloaded APK, jar or web bundle does not
decompile back to readable Kotlin — see [docs/code-hardening.md](docs/code-hardening.md) for what
that does and does not protect, and [docs/release-signing.md](docs/release-signing.md) for keys
and store identities.

- Android APK (R8; signed when a keystore is configured): `./gradlew :androidApp:assembleRelease`
- Android bundle for Play: `./gradlew :androidApp:bundleRelease`
- Desktop app image (ProGuard): `./gradlew :composeApp:createReleaseDistributable`
- Check the hardening held: `./gradlew :androidApp:verifyReleaseObfuscation :composeApp:verifyWebDistributionStripped`

## Running tests

- Feed feature tests: `./gradlew :feature:Feed:jvmTest`
- Server tests: `./gradlew :server:test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
