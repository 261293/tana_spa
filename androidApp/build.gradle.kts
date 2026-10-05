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
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.koin.android)
    debugImplementation(libs.compose.uiTooling)
}
