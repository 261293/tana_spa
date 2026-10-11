package com.tana.spa.feature.feed.data.repository

import com.tana.spa.core.logging.AppLog
import com.tana.spa.feature.feed.data.cache.FeedCache
import com.tana.spa.feature.feed.data.remote.FeedRemoteApi
import com.tana.spa.feature.feed.domain.mapper.toDomain
import com.tana.spa.feature.feed.domain.model.HomeContent
import com.tana.spa.feature.feed.domain.model.HomeSection
import com.tana.spa.feature.feed.domain.repository.FeedRepository
import com.tana.spa.shared.result.AppResult
import com.tana.spa.shared.result.appResultOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

class FeedRepositoryImpl(private val remoteApi: FeedRemoteApi, private val cache: FeedCache) : FeedRepository {

    override val home: Flow<HomeContent> = cache.content

    override suspend fun refresh(): Set<HomeSection> = coroutineScope {
        listOf(
            async {
                load(HomeSection.BANNERS, { remoteApi.banners().map { it.toDomain() } }) { copy(banners = it) }
            },
            async {
                load(HomeSection.CATEGORIES, { remoteApi.categories().map { it.toDomain() } }) { copy(categories = it) }
            },
            async {
                load(HomeSection.FEATURED_SERVICES, { remoteApi.featuredServices().map { it.toDomain() } }) {
                    copy(featuredServices = it)
                }
            },
            async {
                load(HomeSection.PRODUCTS, { remoteApi.products().map { it.toDomain() } }) { copy(products = it) }
            },
            async {
                load(HomeSection.APPOINTMENTS, { remoteApi.appointments().map { it.toDomain() } }) { copy(appointments = it) }
            },
        ).awaitAll().filterNotNull().toSet()
    }

    /** @return [section] when it failed, null when it was stored. */
    private suspend fun <T> load(
        section: HomeSection,
        fetch: suspend () -> List<T>,
        store: HomeContent.(List<T>) -> HomeContent,
    ): HomeSection? = when (val result = appResultOf { fetch() }) {
        is AppResult.Success -> {
            cache.update { it.store(result.value) }
            null
        }

        is AppResult.Failure -> {
            AppLog.w(LOG_TAG, result.error.cause) { "section $section failed: ${result.error.message}" }
            section
        }
    }

    private companion object {
        const val LOG_TAG = "FeedRepository"
    }
}
