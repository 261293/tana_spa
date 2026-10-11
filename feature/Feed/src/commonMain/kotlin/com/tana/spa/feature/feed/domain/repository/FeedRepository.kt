package com.tana.spa.feature.feed.domain.repository

import com.tana.spa.feature.feed.domain.model.HomeContent
import com.tana.spa.feature.feed.domain.model.HomeSection
import kotlinx.coroutines.flow.Flow

/** Offline-first: the local cache is the single source of truth, the network only refreshes it. */
interface FeedRepository {
    /** Emits the cached home content and every later change to it. */
    val home: Flow<HomeContent>

    /**
     * Fetches each section on its own and replaces that section in the cache.
     *
     * @return the sections that could not be fetched. A failed section keeps what the cache held.
     */
    suspend fun refresh(): Set<HomeSection>
}
