package com.tana.spa.shared

import android.os.Build
import com.tana.spa.shared.helpers.PlatformEnum

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val type: PlatformEnum = PlatformEnum.ANDROID
}

actual fun getPlatform(): Platform = AndroidPlatform()
