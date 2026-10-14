package com.tana.spa.shared

import com.tana.spa.shared.helpers.PlatformEnum

class WasmPlatform : Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val type: PlatformEnum = PlatformEnum.WEB
}

actual fun getPlatform(): Platform = WasmPlatform()

// Read through js(...) rather than an external declaration: an absent global must leave the
// module instantiable, which a missing external would not.
private fun hostApiBaseUrl(): String? = js("window.TANA_API_BASE_URL || null")

actual fun configuredApiBaseUrl(): String? = hostApiBaseUrl()?.takeIf { it.isNotBlank() }
