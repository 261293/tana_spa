package com.tana.spa.shared

import com.tana.spa.shared.helpers.PlatformEnum

interface Platform {
    val name: String
    val type: PlatformEnum
}

expect fun getPlatform(): Platform
