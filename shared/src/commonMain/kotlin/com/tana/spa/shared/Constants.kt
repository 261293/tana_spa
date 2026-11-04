package com.tana.spa.shared

object Constants {
    const val APP_NAME = "Tana SPA"
    const val BASE_URL = "https://jsonplaceholder.typicode.com"

    private const val SUPABASE_URL = "https://jnlonvutjugxxxsfpeom.supabase.co"

    /** Public by design: it identifies the project, and the database functions decide what a caller may do. */
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_OH8tYugWOYtJEynk0AElHA_rB18Sy3t"

    /** Without a trailing slash. The browser build can be pointed elsewhere by the page; see [configuredApiBaseUrl]. */
    fun apiBaseUrl(): String = configuredApiBaseUrl() ?: SUPABASE_URL
}
