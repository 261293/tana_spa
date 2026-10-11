import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.ktlint) apply false
}

// Code style: one ktlint setup for every module (rules live in .editorconfig).
//   ./gradlew ktlintCheck    verify (what CI runs)
//   ./gradlew ktlintFormat   auto-fix what can be fixed
// The root project is included so build.gradle.kts / settings.gradle.kts are linted too.
allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<KtlintExtension> {
        // `ktlint` is a catalog group (it also holds `ktlint-gradle`), hence asProvider().
        version.set(rootProject.libs.versions.ktlint.asProvider())
        android.set(false)
        ignoreFailures.set(false)
    }

    // Generated code (compose resource accessors, SQLDelight queries) is not ours to format.
    // The extension's `filter {}` does not reach these generated source sets, so exclude on the
    // tasks that do the linting/formatting. invariantSeparatorsPath keeps this working on Windows.
    tasks.withType<BaseKtLintCheckTask>().configureEach {
        exclude { element -> element.file.invariantSeparatorsPath.contains("/build/") }
    }
}
