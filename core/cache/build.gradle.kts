plugins {
    id("convention.kmp.multiplatform")
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.sqldelight.runtime)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }
        // No SQLDelight driver on the web; the browser's own storage stands in for one.
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}

sqldelight {
    databases {
        create("CacheDatabase") {
            packageName.set("com.tana.spa.core.cache.db")
        }
    }
}
