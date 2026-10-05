package com.tana.spa.feature.feed.domain.repository

import com.tana.spa.feature.feed.domain.model.DomainFeed
import com.tana.spa.shared.result.AppResult
import kotlinx.coroutines.flow.Flow

/** Offline-first: the local cache is the single source of truth, the network only refreshes it. */
interface FeedRepository {
    /** Emits the cached feed and every later change to it. */
    val feed: Flow<List<DomainFeed>>

    /** Fetches from the network and replaces the cache. The cache is untouched on failure. */
    suspend fun refresh(): AppResult<Unit>
}
