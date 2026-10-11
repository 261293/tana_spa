package com.tana.spa.feature.feed.data.cache.source

import com.tana.spa.core.cache.KeyValueCache
import com.tana.spa.feature.feed.data.cache.FeedCache
import com.tana.spa.feature.feed.domain.model.HomeContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FeedCacheImpl(@Suppress("unused") private val cache: KeyValueCache) : FeedCache {
    // In-memory for now; swap for a SQLDelight table to persist across launches.
    private val state = MutableStateFlow(HomeContent())

    override val content: Flow<HomeContent> = state

    override suspend fun update(transform: (HomeContent) -> HomeContent) {
        state.update(transform)
    }
}
