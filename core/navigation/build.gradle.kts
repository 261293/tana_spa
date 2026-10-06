plugins {
    id("convention.kmp.multiplatform")
    id("convention.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.logging)
            implementation(libs.compose.runtime)
        }
    }
}
