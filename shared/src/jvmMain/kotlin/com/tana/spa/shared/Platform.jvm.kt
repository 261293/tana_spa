package com.tana.spa.shared

import com.tana.spa.shared.helpers.PlatformEnum

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val type: PlatformEnum = PlatformEnum.JVM
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun configuredApiBaseUrl(): String? = null
