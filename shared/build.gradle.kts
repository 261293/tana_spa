plugins {
    id("convention.kmp.multiplatform")
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            // `api`: the auth DTOs are @Serializable and consumed by both the server and the clients.
            api(libs.kotlinx.serialization.json)
        }
    }
}
