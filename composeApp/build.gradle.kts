import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

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
            implementation(projects.core.logging)
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

        nativeDistributions {
            // jpackage only bundles the JDK modules it can detect from the jars, and nothing in
            // the app references java.sql directly - SQLDelight's JDBC driver (core:cache) loads
            // it at runtime. Without this the packaged app starts and then dies on
            // NoClassDefFoundError: java/sql/DriverManager.
            modules("java.sql")
        }

        // A packaged desktop app is a jar: without this, any decompiler reads it straight back
        // to near-source Kotlin. ProGuard shrinks, optimizes and renames it first. The plugin's
        // own default-compose-desktop-rules.pro already covers Compose, Skiko, coroutines and
        // kotlinx.serialization; compose-desktop.pro adds what this app's JVM stack needs.
        // Only the `*Release` tasks run it, so `:composeApp:run` stays fast and debuggable.
        buildTypes.release.proguard {
            isEnabled.set(true)
            obfuscate.set(true)
            optimize.set(true)
            configurationFiles.from(project.file("compose-desktop.pro"))
        }
    }
}

// The production web bundle must ship no source maps: a .map file next to composeApp.js hands
// a reader the unminified JS glue and every original name. Binaryen (on by default) already
// drops the name section from the production .wasm. Dev builds keep their maps.
// The production link task then warns "source-map-embed-sources argument has no effect without
// source map", because the Kotlin plugin sets that option unconditionally; that is expected.
tasks.withType<KotlinJsIrLink>().configureEach {
    if (name.contains("Production")) {
        compilerOptions.sourceMap.set(false)
    }
}
tasks.withType<KotlinWebpack>().configureEach {
    if (name.contains("Production")) {
        sourceMaps = false
    }
}

// Guards the above: a .map in the distribution would undo it silently, and the web target is the
// one platform that serves its own artifacts straight to the client.
tasks.register("verifyWebDistributionStripped") {
    group = "verification"
    description = "Fails if the production web distribution contains source maps."
    dependsOn("wasmJsBrowserDistribution")

    val distribution = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")

    doLast {
        val dir = distribution.get().asFile
        require(dir.isDirectory) { "No web distribution at $dir" }
        val maps = dir.walkTopDown().filter { it.isFile && it.name.endsWith(".map") }.toList()
        require(maps.isEmpty()) {
            "Source maps in the production web bundle: ${maps.joinToString { it.name }}"
        }
        logger.lifecycle("${dir.name}: no source maps")
    }
}
