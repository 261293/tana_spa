package com.tana.spa.feature.feed.data.cache

import com.tana.spa.feature.feed.domain.model.DomainFeed
import kotlinx.coroutines.flow.Flow

interface FeedCache {
    val items: Flow<List<DomainFeed>>
    suspend fun replaceAll(items: List<DomainFeed>)
}
