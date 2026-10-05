package com.tana.spa.shared

import com.tana.spa.shared.helpers.PlatformEnum

class WasmPlatform : Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val type: PlatformEnum = PlatformEnum.WEB
}

actual fun getPlatform(): Platform = WasmPlatform()
