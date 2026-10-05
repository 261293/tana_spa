package com.tana.spa.core.cache.di

import org.koin.core.module.Module
import org.koin.dsl.module

/** Binds [com.tana.spa.core.cache.KeyValueCache] via the platform's driver. */
val cacheModule: Module = module {
    includes(cachePlatformModule)
}

/** Each target provides the concrete cache (SQLDelight driver, or in-memory on web). */
expect val cachePlatformModule: Module
