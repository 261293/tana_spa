plugins {
    id("convention.kmp.multiplatform")
    id("convention.kmp.compose")
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    // The convention plugin names the Android namespace after the module, and `core:notifications`
    // already took `com.tana.spa.notifications`; AGP requires one namespace per module.
    androidLibrary {
        namespace = "com.tana.spa.feature.notifications"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.logging)
            implementation(projects.core.mvi)
            implementation(projects.core.navigation)
            implementation(projects.core.notifications)
            implementation(projects.core.ui)
            implementation(projects.shared)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            // The view-model test drives the real PushRegistrar over a fake API, so the cache
            // comes from core:cache rather than a second fake of it.
            implementation(projects.core.cache)
            implementation(projects.core.network)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "com.tana.spa.feature.notifications.resources"
}
