package com.tana.spa.feature.feed.presentation

import com.tana.spa.feature.feed.TestFixtures
import com.tana.spa.feature.feed.domain.model.DomainFeed
import com.tana.spa.feature.feed.domain.repository.FeedRepository
import com.tana.spa.feature.feed.presentation.manipulator.FeedEffect
import com.tana.spa.feature.feed.presentation.manipulator.FeedViewmodel
import com.tana.spa.shared.result.AppError
import com.tana.spa.shared.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewmodelTest {

    private class FakeRepository(var result: AppResult<Unit>) : FeedRepository {
        val cache = MutableStateFlow<List<DomainFeed>>(emptyList())
        override val feed: Flow<List<DomainFeed>> = cache
        override suspend fun refresh(): AppResult<Unit> {
            if (result is AppResult.Success) cache.value = listOf(TestFixtures.domain)
            return result
        }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loads_feed_on_init() {
        val vm = FeedViewmodel(FakeRepository(AppResult.Success(Unit)))

        assertEquals(listOf("1"), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.isRefreshing)
        assertNull(vm.state.value.error)
    }

    @Test
    fun failure_with_empty_cache_sets_error() {
        val vm = FeedViewmodel(FakeRepository(AppResult.Failure(AppError("boom"))))

        assertEquals("boom", vm.state.value.error)
        assertFalse(vm.state.value.isRefreshing)
    }

    @Test
    fun failure_with_cached_items_emits_effect_not_error() = runTest {
        val repo = FakeRepository(AppResult.Failure(AppError("boom")))
        repo.cache.value = listOf(TestFixtures.domain)
        val vm = FeedViewmodel(repo)

        assertNull(vm.state.value.error)
        assertEquals(FeedEffect.ShowMessage("boom"), vm.effects.first())
    }
}
