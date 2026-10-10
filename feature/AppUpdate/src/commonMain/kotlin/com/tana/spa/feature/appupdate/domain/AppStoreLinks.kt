package com.tana.spa.feature.appupdate.domain

import com.tana.spa.shared.helpers.PlatformEnum

/**
 * Where the update button sends the user. Blank means "not configured": the gate then stays inert,
 * because a forced modal whose only button does nothing cannot be escaped.
 */
object AppStoreLinks {
    const val ANDROID = ""
    const val IOS = ""

    fun forPlatform(platform: PlatformEnum): String = when (platform) {
        PlatformEnum.ANDROID -> ANDROID
        PlatformEnum.IOS -> IOS
        PlatformEnum.JVM, PlatformEnum.WEB -> ""
    }
}
