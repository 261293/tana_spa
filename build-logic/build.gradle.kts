plugins {
    `kotlin-dsl`
}

group = "com.tana.spa.buildlogic"

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.android.kmpLibrary.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpMultiplatform") {
            id = "convention.kmp.multiplatform"
            implementationClass = "convention.KmpMultiplatformConventionPlugin"
        }
        register("kmpCompose") {
            id = "convention.kmp.compose"
            implementationClass = "convention.KmpComposeConventionPlugin"
        }
    }
}
