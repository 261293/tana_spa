package com.tana.spa.core.notifications.di

import com.tana.spa.core.notifications.NoPushTokens
import com.tana.spa.core.notifications.PushPermissions
import com.tana.spa.core.notifications.PushTokens
import com.tana.spa.shared.notifications.DevicePlatform
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The desktop build has no push transport: the notification centre still works (it is a plain
 * authenticated list), and nothing is ever registered or prompted for.
 */
actual val pushPlatformModule: Module = module {
    single<PushPermissions> { PushPermissions.unsupported }
    single<PushTokens> { NoPushTokens(DevicePlatform.DESKTOP) }
}
