import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.androidApplication)
}

// --------------------------------------------------------------------- release signing material
// Credentials come from the environment, or from a gitignored keystore.properties for local
// release builds; the keystore itself never lives in the repo. Nothing is defaulted on purpose:
// with no credentials the release build stays *unsigned* rather than failing, because CI builds
// it only to check that R8 ran and a fork has no keys. See docs/release-signing.md.
val keystoreProperties: Properties? = file("keystore.properties")
    .takeIf { it.isFile }
    ?.let { propertiesFile -> Properties().apply { propertiesFile.inputStream().use(::load) } }

fun signingSecret(variable: String, property: String): String? =
    (providers.environmentVariable(variable).orNull ?: keystoreProperties?.getProperty(property))
        ?.takeIf { it.isNotBlank() }

val releaseKeystore = signingSecret("ANDROID_KEYSTORE_FILE", "storeFile")?.let(rootProject::file)
val releaseStorePassword = signingSecret("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingSecret("ANDROID_KEY_ALIAS", "keyAlias")
// A keystore created by `keytool -genkeypair` usually protects store and key with one password.
val releaseKeyPassword = signingSecret("ANDROID_KEY_PASSWORD", "keyPassword") ?: releaseStorePassword
val canSignRelease = releaseKeystore?.isFile == true &&
    releaseStorePassword != null &&
    releaseKeyAlias != null

// AGP 9 provides built-in Kotlin compilation, so no kotlin-android plugin is applied here.
// The Compose plugins ship with the Kotlin Gradle plugin already on the build classpath,
// so they're applied by id (without a version) to avoid a version clash.
apply(plugin = "org.jetbrains.kotlin.plugin.compose")
apply(plugin = "org.jetbrains.compose")

android {
    namespace = "com.tana.spa"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.tana.spa"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // minSdk is 31, so v3 is the scheme every supported device verifies - apksigner
                // adds v2 only when the minimum SDK dips below 28, which is why this stays
                // enabled. v1 (JAR signing) is off: it would only add a readable manifest of
                // every file in the APK and a weaker signature to keep valid.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // Readable stack traces and fast builds matter more than a hardened APK here.
            isMinifyEnabled = false
        }
        getByName("release") {
            // R8 (full mode by default in AGP) shrinks, optimizes and renames everything that
            // is not an entry point or kept by a rule, so a decompiled release APK no longer
            // shows the original package layout or class, method and field names.
            // The keep rules this app needs live in proguard-rules.pro; see docs/code-hardening.md.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            // Null when no credentials were provided: the APK is then built unsigned, and
            // `verifyReleaseObfuscation` says so.
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // AGP otherwise writes the git branch and commit of the build into the APK, which
            // points a reader straight at the source it was built from.
            vcsInfo {
                include = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Guards the obfuscation itself: a release APK that still carries original com.tana.spa.* class
// names means minification silently regressed (isMinifyEnabled flipped, or a keep rule grew too
// broad). CI runs this task so that cannot ship unnoticed.
tasks.register("verifyReleaseObfuscation") {
    group = "verification"
    description = "Fails if the release APK still exposes original class names or debug log messages."
    dependsOn("assembleRelease")

    val apkDirectory = layout.buildDirectory.dir("outputs/apk/release")
    val mappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
    // None of these is reached by name at runtime, so R8 has to have renamed all of them.
    val mustBeRenamed = listOf(
        "com/tana/spa/core/mvi/MviViewModel",
        "com/tana/spa/core/navigation/MutableNavigator",
        "com/tana/spa/feature/auth/data/repository/AuthRepositoryImpl",
        "com/tana/spa/feature/auth/presentation/manipulator/LoginViewModel",
    )
    // Messages from AppLog.d call sites: the -assumenosideeffects rule has to have dropped them.
    val mustBeStripped = listOf(
        "submit ignored: already loading",
        "back ignored at root ",
    )

    doLast {
        val mapping = mappingFile.get().asFile
        require(mapping.isFile && mapping.length() > 0L) {
            "No R8 mapping at $mapping - is isMinifyEnabled still true for the release build type?"
        }

        val apkDir = apkDirectory.get().asFile
        val apk = apkDir.listFiles().orEmpty().firstOrNull { it.extension == "apk" }
            ?: error("No release APK in $apkDir")

        // Dex stores class names and string literals as modified UTF-8, so plain byte
        // matching over the raw dex finds any that survived.
        val dex = ZipFile(apk).use { zip ->
            zip.entries().toList()
                .filter { it.name.endsWith(".dex") }
                .map { entry -> zip.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) } }
        }
        val leaked = (mustBeRenamed + mustBeStripped).filter { probe -> dex.any { it.contains(probe) } }
        require(leaked.isEmpty()) {
            "${apk.name} still exposes: ${leaked.joinToString()}. " +
                "Check isMinifyEnabled and the keep rules in proguard-rules.pro."
        }
        // An APK signed with v2/v3 carries the "APK Sig Block 42" magic between its entries and
        // the central directory. Reported, not enforced: CI has no keys and builds unsigned.
        val signed = apk.readBytes().toString(Charsets.ISO_8859_1).contains("APK Sig Block 42")
        logger.lifecycle(
            "${apk.name}: ${dex.size} dex file(s), ${mustBeRenamed.size} probe classes obfuscated, " +
                "${mustBeStripped.size} debug messages stripped, " +
                (if (signed) "signed (v2/v3)" else "UNSIGNED - see docs/release-signing.md") +
                ", mapping at $mapping",
        )
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.core.logging)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.koin.android)
    debugImplementation(libs.compose.uiTooling)
}
