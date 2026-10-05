package com.tana.spa.core.cache.di

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.tana.spa.core.cache.SqlDelightKeyValueCache
import com.tana.spa.core.cache.KeyValueCache
import com.tana.spa.core.cache.db.CacheDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val cachePlatformModule: Module = module {
    single<KeyValueCache> {
        val driver = AndroidSqliteDriver(CacheDatabase.Schema, androidContext(), "cache.db")
        SqlDelightKeyValueCache(CacheDatabase(driver))
    }
}
