package com.tana.spa.feature.feed.presentation

import com.tana.spa.feature.feed.TestFixtures
import com.tana.spa.feature.feed.domain.HomeUserName
import com.tana.spa.feature.feed.domain.model.HomeContent
import com.tana.spa.feature.feed.domain.model.HomeSection
import com.tana.spa.feature.feed.domain.repository.FeedRepository
import com.tana.spa.feature.feed.presentation.data.UiAppointment
import com.tana.spa.feature.feed.presentation.data.UiProduct
import com.tana.spa.feature.feed.presentation.data.UiService
import com.tana.spa.feature.feed.presentation.manipulator.FeedEffect
import com.tana.spa.feature.feed.presentation.manipulator.FeedEvent
import com.tana.spa.feature.feed.presentation.manipulator.FeedViewmodel
import com.tana.spa.feature.feed.presentation.manipulator.SectionState
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewmodelTest {

    private class FakeRepository(
        private val fresh: HomeContent = TestFixtures.content,
        private val failed: Set<HomeSection> = emptySet(),
        private val gate: CompletableDeferred<Unit>? = null,
        initial: HomeContent = HomeContent(),
    ) : FeedRepository {
        private val cache = MutableStateFlow(initial)
        override val home: Flow<HomeContent> = cache

        override suspend fun refresh(): Set<HomeSection> {
            gate?.await()
            cache.value = cache.value.mergedWith(fresh, except = failed)
            return failed
        }

        private fun HomeContent.mergedWith(other: HomeContent, except: Set<HomeSection>) = copy(
            banners = if (HomeSection.BANNERS in except) banners else other.banners,
            categories = if (HomeSection.CATEGORIES in except) categories else other.categories,
            featuredServices = if (HomeSection.FEATURED_SERVICES in except) featuredServices else other.featuredServices,
            products = if (HomeSection.PRODUCTS in except) products else other.products,
            appointments = if (HomeSection.APPOINTMENTS in except) appointments else other.appointments,
        )
    }

    private val noName = HomeUserName { null }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun every_section_is_loading_until_the_first_refresh_finishes() {
        val gate = CompletableDeferred<Unit>()
        val vm = FeedViewmodel(FakeRepository(gate = gate), noName)

        assertEquals(SectionState.Loading, vm.state.value.banners)
        assertEquals(SectionState.Loading, vm.state.value.products)

        gate.complete(Unit)

        assertIs<SectionState.Ready<*>>(vm.state.value.banners)
    }

    @Test
    fun loaded_sections_carry_their_formatted_items() {
        val vm = FeedViewmodel(FakeRepository(), noName)

        assertEquals(
            SectionState.Ready(listOf(UiService("s1", "Facial", "450.000đ", durationMinutes = 60, isCombo = false))),
            vm.state.value.featuredServices,
        )
        assertEquals(SectionState.Ready(listOf(UiProduct("p1", "Serum", "480.000đ"))), vm.state.value.products)
    }

    @Test
    fun a_section_with_nothing_to_show_is_ready_and_empty_so_the_screen_hides_it() {
        val vm = FeedViewmodel(FakeRepository(fresh = TestFixtures.content.copy(appointments = emptyList())), noName)

        assertEquals(SectionState.Ready(emptyList()), vm.state.value.appointments)
    }

    @Test
    fun a_section_with_appointments_shows_them() {
        val vm = FeedViewmodel(FakeRepository(), noName)

        assertEquals(SectionState.Ready(listOf(UiAppointment("a1", "Facial", 1_000L))), vm.state.value.appointments)
    }

    @Test
    fun a_failed_section_with_nothing_cached_is_failed_while_the_others_load() {
        val vm = FeedViewmodel(FakeRepository(failed = setOf(HomeSection.PRODUCTS)), noName)

        assertEquals(SectionState.Failed, vm.state.value.products)
        assertIs<SectionState.Ready<*>>(vm.state.value.banners)
    }

    @Test
    fun a_failed_section_keeps_showing_what_was_cached() {
        val cached = HomeContent(products = listOf(TestFixtures.product))
        val vm = FeedViewmodel(FakeRepository(failed = setOf(HomeSection.PRODUCTS), initial = cached), noName)

        assertEquals(SectionState.Ready(listOf(UiProduct("p1", "Serum", "480.000đ"))), vm.state.value.products)
    }

    @Test
    fun the_greeting_name_comes_from_the_user_name_provider() {
        val vm = FeedViewmodel(FakeRepository(), HomeUserName { "Lan" })

        assertEquals("Lan", vm.state.value.greetingName)
    }

    @Test
    fun the_assistant_card_asks_for_navigation_through_an_effect() = runTest {
        val vm = FeedViewmodel(FakeRepository(), noName)

        vm.onEvent(FeedEvent.AssistantClicked)

        assertEquals(FeedEffect.OpenAssistant, vm.effects.first())
    }

    @Test
    fun an_item_without_a_destination_says_it_is_coming_soon() = runTest {
        val vm = FeedViewmodel(FakeRepository(), noName)

        vm.onEvent(FeedEvent.ItemClicked)

        assertEquals(FeedEffect.ShowComingSoon, vm.effects.first())
    }
}
