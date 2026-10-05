package com.tana.spa.di

import com.tana.spa.core.cache.di.cacheModule
import com.tana.spa.core.network.networkModule
import com.tana.spa.feature.auth.di.authModule
import com.tana.spa.feature.feed.di.feedModule
import com.tana.spa.shared.di.platformModule
import com.tana.spa.shared.di.sharedModule
import org.koin.core.module.Module

/** The full list of Koin modules contributed by shared, core and feature modules. */
fun appModules(): List<Module> = listOf(
    sharedModule,
    platformModule,
    networkModule,
    cacheModule,
    authModule,
    feedModule,
)
