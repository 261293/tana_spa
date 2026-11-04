package com.tana.spa.shared

import com.tana.spa.shared.helpers.PlatformEnum

object Constants {
    const val APP_NAME = "Tana SPA"
    const val BASE_URL = "https://jsonplaceholder.typicode.com"

    /** Port the Ktor backend listens on. 8080 is left free for the wasm webpack dev server. */
    const val SERVER_PORT = 8081

    private const val SUPABASE_URL = "https://jnlonvutjugxxxsfpeom.supabase.co"

    /** Public by design: it identifies the project, and the database functions decide what a caller may do. */
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_OH8tYugWOYtJEynk0AElHA_rB18Sy3t"

    /** Without a trailing slash. The browser build can be pointed elsewhere by the page; see [configuredApiBaseUrl]. */
    @Suppress("UNUSED_PARAMETER")
    fun apiBaseUrl(platform: PlatformEnum): String = configuredApiBaseUrl() ?: SUPABASE_URL
}
