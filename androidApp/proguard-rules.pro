# R8 configuration for the release APK. Read docs/code-hardening.md first: it explains what
# this buys (and what it does not).
#
# Most of the configuration is not here. The baseline is AGP's proguard-android-optimize.txt
# plus the consumer rules each library ships inside its own artifact - Compose, AndroidX,
# OkHttp, Coroutines, SQLDelight and kotlinx.serialization (whose bundled rules already keep
# every `Companion` / `INSTANCE` / `serializer()` member the runtime looks up, so this file
# must not repeat them). `build/outputs/mapping/release/configuration.txt` lists the merged
# result, annotated with the artifact each section came from.
#
# Keep this file short: every keep rule is something that stays readable in the APK.

# ---------------------------------------------------------------------------- crash reports
# Keep what a stack trace needs, but rename the source file attribute so the original Kotlin
# file names are not shipped. build/outputs/mapping/release/mapping.txt turns an obfuscated
# trace back into a readable one - archive it with every release, it cannot be reproduced later.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ----------------------------------------------------------------------------- debug logging
# AppLog.d message lambdas spell out screens, events, repository calls and navigation routes.
# AppLog.minLevel already drops them at runtime, but the strings would still sit in the APK as
# a free map of the app's internals. -optimize lets R8 delete the calls, after which the
# lambdas are unreachable and go with them. AppLog.i/w/e stay: release builds still need
# breadcrumbs for bug reports, and core:logging keeps those messages free of user data.
-assumenosideeffects class com.tana.spa.core.logging.AppLog {
    public *** d(...);
}
