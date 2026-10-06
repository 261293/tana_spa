package com.tana.spa.core.cache

import com.tana.spa.core.cache.db.CacheDatabase

/** Minimal key/value cache abstraction consumed by feature modules. */
interface KeyValueCache {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun clear()
}

/** SQLDelight-backed implementation shared by all native/JVM targets. */
class SqlDelightKeyValueCache(database: CacheDatabase) : KeyValueCache {
    private val queries = database.cacheQueries

    override fun put(key: String, value: String) {
        queries.upsert(key, value)
    }
    override fun get(key: String): String? = queries.get(key).executeAsOneOrNull()
    override fun clear() {
        queries.clear()
    }
}

/** Fallback used on the web target, where the SQLDelight web-worker driver is not wired yet. */
class InMemoryKeyValueCache : KeyValueCache {
    private val map = mutableMapOf<String, String>()
    override fun put(key: String, value: String) {
        map[key] = value
    }
    override fun get(key: String): String? = map[key]
    override fun clear() = map.clear()
}
