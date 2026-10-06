package com.tana.spa.feature.feed.data.cache.source

import com.tana.spa.core.cache.KeyValueCache
import com.tana.spa.feature.feed.data.cache.FeedCache
import com.tana.spa.feature.feed.domain.model.DomainFeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FeedCacheImpl(@Suppress("unused") private val cache: KeyValueCache) : FeedCache {
    // In-memory for now; swap for a SQLDelight table to persist across launches.
    private val state = MutableStateFlow<List<DomainFeed>>(emptyList())

    override val items: Flow<List<DomainFeed>> = state

    override suspend fun replaceAll(items: List<DomainFeed>) {
        state.value = items
    }
}
