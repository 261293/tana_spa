# Code hardening: making the shipped artifacts hard to read

Anyone who can install the app can also unpack it. An APK is a zip of dex bytecode that `jadx`
turns back into near-source Kotlin in seconds; a desktop jar decompiles even more cleanly. This
page describes what the release builds now do about that, how to verify it, and — just as
important — what it does **not** buy.

**Obfuscation is a cost multiplier, not a security boundary.** It makes reverse engineering
tedious; it never makes it impossible. Two rules follow from that, and no build setting replaces
them:

1. **Never ship a secret in the client.** No API keys, signing keys, OTP pepper or admin
   endpoints in `shared`, `core` or any `feature` module. Today the client holds none: the only
   credential it stores is the user's own session token (`SessionStore`), and `Constants` holds
   nothing but URLs. Keep it that way — a string literal cannot be obfuscated away, it has to be
   there for the code to use it.
2. **Enforce every rule on the server.** The client is a convenience; `server/` is the authority
   on who may do what. Assume a modified client exists.

## What each platform does

| Platform | Measure | Where |
|---|---|---|
| **Android** | R8 in full mode: shrink, optimize, rename every class/method/field that is not an entry point | `androidApp/build.gradle.kts`, `androidApp/proguard-rules.pro` |
| | Resource shrinking, and the git branch/commit metadata AGP embeds is turned off (`vcsInfo`) | same |
| | `AppLog.d` calls (and their message strings) are deleted from the APK | `proguard-rules.pro` |
| | `allowBackup=false`, `usesCleartextTraffic=false` (debug overlay re-allows HTTP for the emulator) | `androidApp/src/main/AndroidManifest.xml` |
| **iOS** | Kotlin/Native compiles to ARM machine code — there is no bytecode to decompile, only a disassembly | `composeApp/build.gradle.kts` (`isStatic = true`) |
| | Release archives strip all local and Swift symbols, so the symbol table stops naming declarations | `iosApp/iosApp.xcodeproj` (`STRIP_INSTALLED_PRODUCT`, `STRIP_STYLE = all`, `STRIP_SWIFT_SYMBOLS`) |
| **Desktop** | ProGuard with obfuscation on for the release distributable | `composeApp/build.gradle.kts`, `composeApp/compose-desktop.pro` |
| **Web** | Logic is WebAssembly, and Binaryen (default for production) drops the name section | `composeApp/build.gradle.kts` |
| | The production bundle ships no `.map` files, which previously carried the unminified JS glue and ~4000 names | same |

The effect on Android is easy to see: the debug APK is 42.7 MB, the release APK 3.0 MB, and
`com.tana.spa.feature.auth.presentation.manipulator.LoginViewModel` becomes something like
`m21`.

One consequence to know about: since the Android and desktop release builds *delete* `AppLog.d`
calls, setting `AppLog.minLevel = LogLevel.DEBUG` in such a build does nothing — debug lines
exist only in debug/dev builds. `i`, `w` and `e` are untouched and still work everywhere.

## What is still readable, on every platform

Plan around this instead of being surprised by it:

- **String literals.** Endpoint paths (`/api/v1/auth/login`), error codes from `AuthErrorCodes`,
  UI copy and `AppLog.i/w/e` messages are all in the artifact in clear text. They have to be.
- **The HTTP contract.** `shared/.../AuthContract.kt` describes the API, and so does one capture
  of the app's traffic. Treat the API as public and authenticate every call.
- **Resources and assets** (`composeResources`, images, fonts) are plain files in the archive.
- **Entry points keep their names**: `MainActivity`, `TanaApplication`, anything named in the
  manifest, plus the `Companion`/`$serializer` members that kotlinx.serialization looks up.
- **Class simple names on web.** `MviViewModel` reads `this::class.simpleName` for its log tag,
  so Kotlin/Wasm keeps those names in the binary. Harmless, but don't expect the `.wasm` to be
  anonymous.

## Verifying it

```bash
# Android: builds the release APK and fails if probe classes or AppLog.d messages survived
./gradlew :androidApp:verifyReleaseObfuscation

# Web: builds the production bundle and fails on any .map file in it
./gradlew :composeApp:verifyWebDistributionStripped

# Desktop: runs ProGuard and produces the obfuscated app image
./gradlew :composeApp:createReleaseDistributable
```

Both `verify*` tasks run in CI (`.github/workflows/ci.yml`), so flipping `isMinifyEnabled` off or
re-enabling source maps fails the build rather than quietly shipping.

To look at a release APK the way an attacker would:

```bash
# class names, string pool, package structure
"$ANDROID_HOME"/cmdline-tools/latest/bin/apkanalyzer dex packages \
  androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk
# or decompile it outright
jadx -d /tmp/out androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk
```

On iOS, `nm -a` on the binary inside the `.app` (and `strings`) is the equivalent check; after a
stripped archive build, Kotlin and Swift declaration names should be gone from the symbol table.

## Release checklist

- **Keep `mapping.txt`.** `androidApp/build/outputs/mapping/release/mapping.txt` is the only way
  to read an obfuscated stack trace, it is not reproducible from a later build, and it must never
  be committed or shipped. Archive it per release (Play Console accepts an upload, and the
  AAB/`DebugSymbols` upload covers the Play crash reports). Not as a public CI artifact: on a
  public repository that hands every reader the deobfuscation key.
- **Sign the release build.** `assembleRelease` currently produces
  `androidApp-release-unsigned.apk` — there is no signing config in the repo on purpose. Keep the
  keystore out of git, feed it from the CI secret store, and prefer Play App Signing.
- **Point the client at HTTPS.** `Constants.apiBaseUrl` returns a `http://localhost`-style dev URL.
  Cleartext is blocked outside debug builds, so a release build must be given the deployed HTTPS
  URL or every call fails.
- **Smoke-test the obfuscated build, not the debug one.** Shrinking breaks exactly the code paths
  nobody tried: sign up, OTP verification, session restore after a restart. Android and desktop
  are the two that rewrite bytecode, so they are the two that can break this way. The first run
  of the desktop image found one such break already: jpackage bundles only the JDK modules it can
  detect in the jars, and nothing references `java.sql` directly (SQLDelight's JDBC driver loads
  it at runtime), so the app died on `NoClassDefFoundError: java/sql/DriverManager` until
  `nativeDistributions.modules("java.sql")` was declared.
- If an extra Xcode configuration is ever added (`Staging`, say), set
  `KOTLIN_FRAMEWORK_BUILD_TYPE = release` on it. `embedAndSignAppleFrameworkForXcode` otherwise
  embeds the **debug** Kotlin framework, which carries full debug information.

## Deliberately not done

These are the next steps if the threat model ever justifies them. Each costs real maintenance,
and none of them is worth adding before the two rules at the top are satisfied:

- **String/class encryption and control-flow flattening** (DexGuard, iXGuard, Obfuscator-LLVM).
  R8 and ProGuard rename; they do not encrypt. This is what raises the bar beyond "tedious".
- **Attestation**: Play Integrity API (Android) and DeviceCheck/App Attest (iOS), verified
  server-side. Unlike anything on this page, this one actually changes what a tampered client can
  do — but only because the *server* checks the verdict.
- **Root/jailbreak, debugger and tamper detection.** Easy to bypass on a device the attacker
  controls, and they add crash-prone native code. Only ever useful as telemetry, never as a gate.
- **Certificate pinning.** Stops casual traffic inspection; costs a forced app update whenever
  the certificate chain changes.
