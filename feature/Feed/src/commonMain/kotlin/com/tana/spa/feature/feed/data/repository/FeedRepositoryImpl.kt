package com.tana.spa.feature.feed.data.repository

import com.tana.spa.feature.feed.data.cache.FeedCache
import com.tana.spa.feature.feed.data.remote.FeedRemoteApi
import com.tana.spa.feature.feed.domain.mapper.toDomain
import com.tana.spa.feature.feed.domain.model.DomainFeed
import com.tana.spa.feature.feed.domain.repository.FeedRepository
import com.tana.spa.shared.result.AppResult
import com.tana.spa.shared.result.appResultOf
import kotlinx.coroutines.flow.Flow

class FeedRepositoryImpl(
    private val remoteApi: FeedRemoteApi,
    private val cache: FeedCache,
) : FeedRepository {

    override val feed: Flow<List<DomainFeed>> = cache.items

    override suspend fun refresh(): AppResult<Unit> = appResultOf {
        cache.replaceAll(remoteApi.fetchFeed().map { it.toDomain() })
    }
}
