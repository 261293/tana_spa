package com.tana.spa.core.cache.di

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.tana.spa.core.cache.SqlDelightKeyValueCache
import com.tana.spa.core.cache.KeyValueCache
import com.tana.spa.core.cache.db.CacheDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

actual val cachePlatformModule: Module = module {
    single<KeyValueCache> {
        val driver = NativeSqliteDriver(CacheDatabase.Schema, "cache.db")
        SqlDelightKeyValueCache(CacheDatabase(driver))
    }
}
