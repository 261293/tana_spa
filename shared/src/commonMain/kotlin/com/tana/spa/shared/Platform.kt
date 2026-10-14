package com.tana.spa.shared

import com.tana.spa.shared.helpers.PlatformEnum

interface Platform {
    val name: String
    val type: PlatformEnum
}

expect fun getPlatform(): Platform

/**
 * Base URL the host environment points the app at, or null to use the compiled default. Only the
 * browser build can be retargeted after it is built, from the page that loads it.
 */
expect fun configuredApiBaseUrl(): String?
