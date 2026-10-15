package com.tana.spa.di

import com.tana.spa.core.cache.di.cacheModule
import com.tana.spa.core.featureflags.di.featureFlagsModule
import com.tana.spa.core.network.networkModule
import com.tana.spa.core.notifications.di.notificationsCoreModule
import com.tana.spa.feature.admin.di.adminModule
import com.tana.spa.feature.appupdate.di.appUpdateModule
import com.tana.spa.feature.assistant.di.assistantModule
import com.tana.spa.feature.auth.di.authModule
import com.tana.spa.feature.booking.di.bookingModule
import com.tana.spa.feature.feed.di.feedModule
import com.tana.spa.feature.notifications.di.notificationsModule
import com.tana.spa.feature.promotions.di.promotionsModule
import com.tana.spa.feature.settings.di.settingsModule
import com.tana.spa.shared.di.platformModule
import com.tana.spa.shared.di.sharedModule
import org.koin.core.module.Module

/** The full list of Koin modules contributed by shared, core and feature modules. */
fun appModules(): List<Module> = listOf(
    sharedModule,
    platformModule,
    networkModule,
    cacheModule,
    appModule,
    featureFlagsModule,
    notificationsCoreModule,
    appUpdateModule,
    authModule,
    assistantModule,
    feedModule,
    notificationsModule,
    bookingModule,
    promotionsModule,
    settingsModule,
    adminModule,
)
