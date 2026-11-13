package com.tana.spa.shared

import com.tana.spa.shared.helpers.PlatformEnum

interface Platform {
    val name: String
    val type: PlatformEnum
}

expect fun getPlatform(): Platform

/**
 * Base URL the host environment points the app at, or null to use the compiled default. The browser
 * build reads it from the page, the desktop build from the environment; mobile builds always use the default.
 */
expect fun configuredApiBaseUrl(): String?

/** Publishable key of the project [configuredApiBaseUrl] names, or null to use the compiled one. */
expect fun configuredPublishableKey(): String?
