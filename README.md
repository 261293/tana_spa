This is a modular Kotlin Multiplatform project targeting **Android, iOS, Web (Wasm) and Desktop (JVM)** with a **Supabase** backend, organized after the layered `core` / `feature` architecture of
[inassar0/kmp_instagram_clone](https://github.com/inassar0/kmp_instagram_clone).

## Module layout

```
build-logic/                 Gradle convention plugins (shared KMP + Compose config)
composeApp/                  Compose Multiplatform app (KMP library): App UI + all
                             non-Android entry points (iosMain, jvmMain, webMain) + Koin init
androidApp/                  Android application module (APK) that wraps composeApp
iosApp/                      Xcode project (SwiftUI host for the ComposeApp framework)
shared/                      Non-UI shared code: Platform expect/actual, Greeting,
                             Constants, DI (sharedModule), helpers, and the spa catalogue
                             (treatments, therapists, offers, SpaDate) the booking screens read
core/
  cache/                     KeyValueCache abstraction (SQLDelight; in-memory on wasmJs)
  featureflags/              FeatureFlags resolution (per-user override / DB / compiled default)
  logging/                   AppLog + platform sinks
  mvi/                       MviViewModel base (State / Event / Effect)
  navigation/                FeatureEntry / Navigator / Routes
  network/                   Ktor client + per-platform engine (PlatformClientEngine)
  notifications/             Push permission/token per platform, device registration, unread badge
  ui/                        AppTheme, SpaColors/SpaTheme tokens, AppScaffold, NavBar, AppBars,
                             SpaIcon line icons and the felt/pocket/cork/topo surfaces
feature/
  Admin/                     Staff console: booking requests + therapist schedule, discount codes
  AppUpdate/                 Force/optional update gate: an overlay above every route, not a
                             destination, so it has no FeatureEntry (docs/adr/0003)
  Booking/                   Guest booking flow: treatment, therapist, date & time, review
  Notifications/             Notification centre: list, unread state, in-context push opt-in
                             (docs/notifications.md)
  Promotions/                Offers and discount codes
  Settings/                  Profile, notification preferences, language, sign out
  Assistant, Auth, Explore,  Entry + Screen per feature
  Likes, NewPost, Profile
  Feed/                      Full clean-arch reference: data (remote/cache/repository),
                             domain (model/mapper/repository), presentation (ui + MVI viewmodel)
supabase/                    The backend (Supabase CLI layout): migrations/ (schema, rules and
                             every database function), tests/database/ (pgTAP), functions/push
                             and functions/accounts (Edge Functions, Deno tests beside them)
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

**New here?** [docs/local-development.md](docs/local-development.md) has every command in order:
first-time setup, the daily loop, what to run before you push, and what to do when a build fails.

The apps talk to the Supabase project named in `shared/.../Constants.kt`; nothing has to run
locally for them. The backend in `supabase/` reaches that project only once it is deployed (see
[docs/production-checklist.md](docs/production-checklist.md)).

- Web (dev server): `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` → http://localhost:8080
- Web (production bundle): `./gradlew :composeApp:wasmJsBrowserDistribution`
- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app: `./gradlew :composeApp:run`
- iOS app: open [/iosApp](./iosApp) in Xcode and run it (requires macOS/Xcode).
- Admin page (feature flags, app update config): `/admin/` of the web build, from
  `composeApp/src/webMain/resources/admin/index.html`

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

What CI runs, and what to run before pushing ([all of it, in order](docs/local-development.md#3-before-you-push)):

```bash
./gradlew ktlintFormat && ./gradlew ktlintCheck   # style gate
./gradlew jvmTest                                 # Kotlin unit tests
./gradlew :composeApp:compileKotlinWasmJs         # strictest target; required for commonMain changes

npx supabase start -x logflare,vector,studio      # the local stack (Docker), once per session
npx supabase test db                              # database tests (pgTAP)
```

- One module: `./gradlew :feature:Auth:jvmTest`
- One test: `./gradlew :feature:Auth:jvmTest --tests "com.tana.spa.feature.auth.presentation.LoginViewModelTest"`
- Edge Functions, with the stack running (it already serves them):
  `deno test --allow-net --allow-env --allow-run supabase/functions`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
