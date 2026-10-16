package com.tana.spa.core.cache.di

import com.tana.spa.core.cache.KeyValueCache
import com.tana.spa.core.cache.LocalStorageKeyValueCache
import org.koin.core.module.Module
import org.koin.dsl.module

// The SQLDelight web-worker driver needs an npm worker bundle; until that is wired, the web target
// stores its keys in localStorage, which survives a reload the way the SQLite file does elsewhere.
actual val cachePlatformModule: Module = module {
    single<KeyValueCache> { LocalStorageKeyValueCache() }
}
