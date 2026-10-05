package com.tana.spa.shared

import com.tana.spa.shared.helpers.PlatformEnum
import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val type: PlatformEnum = PlatformEnum.IOS
}

actual fun getPlatform(): Platform = IOSPlatform()
