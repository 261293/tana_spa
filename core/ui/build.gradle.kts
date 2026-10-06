plugins {
    id("convention.kmp.multiplatform")
    id("convention.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }
    }
}

compose.resources {
    // Fonts and shared drawables are consumed from feature modules, so the Res class is public.
    publicResClass = true
    packageOfResClass = "com.tana.spa.core.ui.resources"
}
