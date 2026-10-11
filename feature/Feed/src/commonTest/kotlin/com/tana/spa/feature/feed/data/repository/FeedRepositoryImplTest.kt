package com.tana.spa.feature.feed.data.repository

import com.tana.spa.feature.feed.TestFixtures
import com.tana.spa.feature.feed.data.cache.FeedCache
import com.tana.spa.feature.feed.data.remote.FeedRemoteApi
import com.tana.spa.feature.feed.data.remote.dto.AppointmentDto
import com.tana.spa.feature.feed.data.remote.dto.BannerDto
import com.tana.spa.feature.feed.data.remote.dto.ProductDto
import com.tana.spa.feature.feed.data.remote.dto.ServiceCategoryDto
import com.tana.spa.feature.feed.data.remote.dto.ServiceDto
import com.tana.spa.feature.feed.domain.model.HomeContent
import com.tana.spa.feature.feed.domain.model.HomeSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedRepositoryImplTest {

    private class FakeCache(initial: HomeContent = HomeContent()) : FeedCache {
        private val state = MutableStateFlow(initial)
        override val content: Flow<HomeContent> = state
        override suspend fun update(transform: (HomeContent) -> HomeContent) = state.update(transform)
    }

    private class FakeRemote(private val failing: Set<HomeSection> = emptySet()) : FeedRemoteApi {
        private fun check(section: HomeSection) {
            if (section in failing) error("offline")
        }

        override suspend fun banners(): List<BannerDto> = listOf(TestFixtures.bannerDto).also { check(HomeSection.BANNERS) }
        override suspend fun categories(): List<ServiceCategoryDto> =
            listOf(TestFixtures.categoryDto).also { check(HomeSection.CATEGORIES) }

        override suspend fun featuredServices(): List<ServiceDto> =
            listOf(TestFixtures.serviceDto).also { check(HomeSection.FEATURED_SERVICES) }

        override suspend fun products(): List<ProductDto> = listOf(TestFixtures.productDto).also { check(HomeSection.PRODUCTS) }
        override suspend fun appointments(): List<AppointmentDto> =
            listOf(TestFixtures.appointmentDto).also { check(HomeSection.APPOINTMENTS) }
    }

    @Test
    fun refresh_maps_every_section_into_the_cache_and_reports_no_failure() = runTest {
        val repository = FeedRepositoryImpl(FakeRemote(), FakeCache())

        val failed = repository.refresh()

        assertEquals(emptySet(), failed)
        assertEquals(TestFixtures.content, repository.home.first())
    }

    @Test
    fun a_failing_section_is_reported_and_the_others_still_refresh() = runTest {
        val repository = FeedRepositoryImpl(FakeRemote(failing = setOf(HomeSection.PRODUCTS)), FakeCache())

        val failed = repository.refresh()

        assertEquals(setOf(HomeSection.PRODUCTS), failed)
        assertEquals(TestFixtures.content.copy(products = emptyList()), repository.home.first())
    }

    @Test
    fun a_failing_section_keeps_what_the_cache_already_held() = runTest {
        val cached = TestFixtures.content.copy(products = listOf(TestFixtures.product.copy(name = "Cached")))
        val repository = FeedRepositoryImpl(FakeRemote(failing = setOf(HomeSection.PRODUCTS)), FakeCache(cached))

        repository.refresh()

        assertEquals(cached.products, repository.home.first().products)
    }
}
