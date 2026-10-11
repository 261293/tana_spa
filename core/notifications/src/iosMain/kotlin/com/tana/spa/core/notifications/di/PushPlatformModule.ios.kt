package com.tana.spa.core.notifications.di

import com.tana.spa.core.notifications.IosPushPermissions
import com.tana.spa.core.notifications.IosPushTokens
import com.tana.spa.core.notifications.PushPermissions
import com.tana.spa.core.notifications.PushTokens
import org.koin.core.module.Module
import org.koin.dsl.module

actual val pushPlatformModule: Module = module {
    single<PushPermissions> { IosPushPermissions() }
    single<PushTokens> { IosPushTokens() }
}
