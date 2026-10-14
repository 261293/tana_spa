package com.tana.spa.shared

import com.tana.spa.shared.helpers.PlatformEnum

object Constants {
    const val APP_NAME = "Tana SPA"
    const val BASE_URL = "https://jsonplaceholder.typicode.com"

    /** Port the Ktor backend listens on. 8080 is left free for the wasm webpack dev server. */
    const val SERVER_PORT = 8081

    /**
     * Backend base URL, without a trailing slash. The Android emulator reaches the host machine
     * through 10.0.2.2; the iOS simulator and the browser share the host's `localhost`. The
     * browser build is pointed at a deployed backend by the page instead, so the same bundle can
     * be published anywhere without rebuilding; see [configuredApiBaseUrl].
     */
    fun apiBaseUrl(platform: PlatformEnum): String = configuredApiBaseUrl() ?: when (platform) {
        PlatformEnum.ANDROID -> "http://10.0.2.2:$SERVER_PORT"
        PlatformEnum.IOS, PlatformEnum.JVM, PlatformEnum.WEB -> "http://localhost:$SERVER_PORT"
    }
}
