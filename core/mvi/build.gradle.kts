plugins {
    id("convention.kmp.multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.logging)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
