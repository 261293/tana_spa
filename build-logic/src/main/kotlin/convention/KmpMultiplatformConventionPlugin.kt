package convention

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Applies the shared Kotlin Multiplatform configuration used by every
 * core, feature and shared module:
 *
 *  - targets: androidLibrary, jvm, iosArm64, iosSimulatorArm64, wasmJs (browser)
 *  - a per-module Android namespace derived from the project name
 *  - the common test dependency
 */
class KmpMultiplatformConventionPlugin : Plugin<Project> {

    @OptIn(ExperimentalWasmDsl::class)
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.kotlin.multiplatform.library")
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        extensions.configure<KotlinMultiplatformExtension> {
            iosArm64()
            iosSimulatorArm64()
            jvm()
            wasmJs { browser() }

            // AGP Kotlin-Multiplatform Android library target (kotlin { androidLibrary { } }).
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
                namespace = "com.tana.spa.${project.name.lowercase()}"
                compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
                minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
                // Compose Multiplatform ships a module's resources as Android assets; the KMP
                // library plugin leaves Android resources off, so without this the APK has no
                // composeResources and the first stringResource() call crashes at launch.
                androidResources { enable = true }
            }

            sourceSets.apply {
                getByName("commonTest").dependencies {
                    implementation(libs.findLibrary("kotlin-test").get())
                }
            }
        }
    }
}
