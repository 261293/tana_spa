plugins {
    id("convention.kmp.multiplatform")
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.logging)
            implementation(projects.core.cache)
            implementation(projects.core.network)
            // `api`: FeatureFlag/FeatureFlags are part of this module's surface, and every feature
            // module that gates something injects them.
            api(projects.shared)

            implementation(libs.ktor.clientCore)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.clientMock)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.serializationJson)
        }
    }
}
