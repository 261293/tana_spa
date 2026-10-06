import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("convention.kmp.multiplatform")
    id("convention.kmp.compose")
}

// The convention plugin already registers the androidLibrary, jvm, iosArm64,
// iosSimulatorArm64 and wasmJs(browser) targets. Here we layer on the app-specific
// bits: an executable web bundle, iOS frameworks and the desktop entry point.
kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        // Intermediate source set shared by all browser targets (currently wasmJs).
        val webMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
        wasmJsMain {
            dependsOn(webMain)
        }

        commonMain.dependencies {
            implementation(projects.shared)
            implementation(projects.core.ui)
            implementation(projects.core.navigation)
            implementation(projects.core.network)
            implementation(projects.core.cache)
            implementation(projects.feature.assistant)
            implementation(projects.feature.auth)
            implementation(projects.feature.explore)
            implementation(projects.feature.feed)
            implementation(projects.feature.likes)
            implementation(projects.feature.newPost)
            implementation(projects.feature.profile)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.tana.spa.MainKt"
    }
}
