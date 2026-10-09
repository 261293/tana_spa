# ProGuard configuration for the release desktop distributable (see docs/code-hardening.md).
#
# The Compose Gradle plugin already applies default-compose-desktop-rules.pro, which covers
# Kotlin, Skiko, Compose, coroutines and kotlinx.serialization. These are the additions this
# app's own JVM stack needs; without them ProGuard either warns itself into a build failure or
# removes something that is only reached reflectively at runtime.

# -------------------------------------------------------------------------- OkHttp / Ktor
# The JVM HTTP client is Ktor-on-OkHttp. OkHttp compiles against TLS providers, a GraalVM
# native-image feature and an Android platform that are all absent from the desktop classpath;
# it picks what exists at runtime. ProGuard treats such references as errors unless told not to.
-dontwarn okhttp3.internal.platform.**
-dontwarn okhttp3.internal.graal.**
-dontwarn org.graalvm.**
-dontwarn com.oracle.svm.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn io.ktor.**

# ------------------------------------------------------------------------------- Material 3
# material3 1.11.0-alpha07 (see gradle/libs.versions.toml) was built against a newer
# compose-foundation than 1.12.1, so these two defaults objects reference foundation methods
# that are not on the classpath. This app uses neither - nothing calls into them, and shrinking
# removes them - but ProGuard refuses to continue while the references are unresolved.
-dontwarn androidx.compose.material3.OutlinedTextFieldDefaults
-dontwarn androidx.compose.material3.TextFieldDefaults

# ---------------------------------------------------------------------------- SQLite (cache)
# core:cache uses SQLDelight's JDBC driver on the desktop. sqlite-jdbc loads its native library
# and its JDBC driver class by name, so neither may be renamed or shrunk away.
-keep class org.sqlite.** { *; }
-dontwarn org.sqlite.**

# --------------------------------------------------------------------------- Dispatchers.Main
# kotlinx-coroutines-swing publishes the desktop main dispatcher through
# META-INF/services, so nothing references SwingDispatcherFactory by name. The plugin's default
# rules keep the class but not its members; ServiceLoader needs the constructor, and when it
# cannot instantiate the factory every `viewModelScope.launch` fails with
# "Module with the Main dispatcher is missing".
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory { *; }

# ------------------------------------------------------------------------------ debug logging
# Same reasoning as androidApp/proguard-rules.pro: AppLog.d messages name screens, events and
# routes, and would stay readable in the jar even though minLevel drops them at runtime.
-assumenosideeffects class com.tana.spa.core.logging.AppLog {
    public *** d(...);
}
