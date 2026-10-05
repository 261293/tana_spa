package com.tana.spa.core.cache.di

import com.tana.spa.core.cache.InMemoryKeyValueCache
import com.tana.spa.core.cache.KeyValueCache
import org.koin.core.module.Module
import org.koin.dsl.module

// The SQLDelight web-worker driver needs an npm worker bundle; until that is wired,
// the web target uses a simple in-memory cache so the browser build stays self-contained.
actual val cachePlatformModule: Module = module {
    single<KeyValueCache> { InMemoryKeyValueCache() }
}
