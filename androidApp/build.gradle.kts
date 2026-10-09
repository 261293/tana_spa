import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.androidApplication)
}

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
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
        logger.lifecycle(
            "${apk.name}: ${dex.size} dex file(s), ${mustBeRenamed.size} probe classes obfuscated, " +
                "${mustBeStripped.size} debug messages stripped, mapping at $mapping",
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
