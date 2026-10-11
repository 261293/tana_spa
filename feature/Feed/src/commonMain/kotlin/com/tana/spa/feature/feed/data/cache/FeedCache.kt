package com.tana.spa.feature.feed.data.cache

import com.tana.spa.feature.feed.domain.model.HomeContent
import kotlinx.coroutines.flow.Flow

interface FeedCache {
    val content: Flow<HomeContent>
    suspend fun update(transform: (HomeContent) -> HomeContent)
}
