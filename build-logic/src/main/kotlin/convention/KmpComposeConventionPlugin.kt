package convention

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Adds Compose Multiplatform on top of [KmpMultiplatformConventionPlugin].
 * Apply this in UI-bearing modules (core UI, every feature, composeApp).
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.compose")
            apply("org.jetbrains.kotlin.plugin.compose")
        }
    }
}
