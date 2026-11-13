package com.tana.spa.shared

object Constants {
    const val APP_NAME = "Tana SPA"

    /** The Supabase project every app calls: REST at `/rest/v1`, Auth at `/auth/v1`, Edge Functions at `/functions/v1`. */
    const val BASE_URL = "https://jnlonvutjugxxxsfpeom.supabase.co"

    /** Public by design: it identifies the project, and the database functions decide what a caller may do. */
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_OH8tYugWOYtJEynk0AElHA_rB18Sy3t"

    /** Without a trailing slash. The web and desktop builds can be pointed elsewhere; see [configuredApiBaseUrl]. */
    fun apiBaseUrl(): String = configuredApiBaseUrl() ?: BASE_URL

    fun publishableKey(): String = configuredPublishableKey() ?: SUPABASE_PUBLISHABLE_KEY
}
