plugins {
    id("convention.kmp.multiplatform")
    id("convention.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
        }
    }
}
