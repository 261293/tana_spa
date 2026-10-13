package com.tana.spa.core.notifications.di

import com.tana.spa.core.notifications.AndroidPushPermissions
import com.tana.spa.core.notifications.AndroidPushTokens
import com.tana.spa.core.notifications.PostNotificationsPrompt
import com.tana.spa.core.notifications.PushPermissions
import com.tana.spa.core.notifications.PushTokens
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val pushPlatformModule: Module = module {
    single<PushPermissions> {
        // Null until the app shell binds an Activity-backed prompt; the permission can still be
        // read, so a device that already granted it registers normally.
        AndroidPushPermissions(androidContext(), getOrNull<PostNotificationsPrompt>())
    }
    single<PushTokens> { AndroidPushTokens(androidContext()) }
}
