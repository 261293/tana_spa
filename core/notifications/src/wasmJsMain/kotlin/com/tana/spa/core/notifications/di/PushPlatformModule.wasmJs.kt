package com.tana.spa.core.notifications.di

import com.tana.spa.core.notifications.PushPermissions
import com.tana.spa.core.notifications.PushTokens
import com.tana.spa.core.notifications.WebPushPermissions
import com.tana.spa.core.notifications.WebPushTokens
import org.koin.core.module.Module
import org.koin.dsl.module

actual val pushPlatformModule: Module = module {
    single<PushPermissions> { WebPushPermissions() }
    single<PushTokens> { WebPushTokens() }
}
