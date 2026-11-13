package com.tana.spa.shared

import com.tana.spa.shared.helpers.PlatformEnum

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val type: PlatformEnum = PlatformEnum.JVM
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun configuredApiBaseUrl(): String? = environment("TANA_API_BASE_URL")

actual fun configuredPublishableKey(): String? = environment("TANA_SUPABASE_KEY")

private fun environment(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
