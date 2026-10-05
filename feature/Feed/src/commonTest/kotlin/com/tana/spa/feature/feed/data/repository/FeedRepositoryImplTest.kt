package com.tana.spa.feature.feed.data.repository

import com.tana.spa.feature.feed.TestFixtures
import com.tana.spa.feature.feed.data.cache.FeedCache
import com.tana.spa.feature.feed.data.remote.FeedRemoteApi
import com.tana.spa.feature.feed.data.remote.dto.feed.response.FeedResponseDto
import com.tana.spa.feature.feed.domain.model.DomainFeed
import com.tana.spa.shared.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FeedRepositoryImplTest {

    private class FakeCache(initial: List<DomainFeed> = emptyList()) : FeedCache {
        private val state = MutableStateFlow(initial)
        override val items: Flow<List<DomainFeed>> = state
        override suspend fun replaceAll(items: List<DomainFeed>) { state.value = items }
    }

    @Test
    fun refresh_maps_and_replaces_cache() = runTest {
        val remote = object : FeedRemoteApi {
            override suspend fun fetchFeed() = listOf(FeedResponseDto(id = 1, title = "t"))
        }
        val repository = FeedRepositoryImpl(remote, FakeCache())

        assertIs<AppResult.Success<Unit>>(repository.refresh())
        assertEquals(listOf(TestFixtures.domain), repository.feed.first())
    }

    @Test
    fun refresh_failure_keeps_cached_data() = runTest {
        val remote = object : FeedRemoteApi {
            override suspend fun fetchFeed(): List<FeedResponseDto> = error("offline")
        }
        val repository = FeedRepositoryImpl(remote, FakeCache(listOf(TestFixtures.domain)))

        val result = repository.refresh()

        assertIs<AppResult.Failure>(result)
        assertEquals("offline", result.error.message)
        assertEquals(listOf(TestFixtures.domain), repository.feed.first())
    }
}
