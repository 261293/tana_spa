package com.tana.spa.feature.booking

import com.tana.spa.feature.booking.presentation.BookingEffect
import com.tana.spa.feature.booking.presentation.BookingEvent
import com.tana.spa.feature.booking.presentation.BookingViewModel
import com.tana.spa.shared.spa.PromoCheck
import com.tana.spa.shared.spa.SpaCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSpaCatalogRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** The catalogue loads on creation, so every test starts from a menu that has arrived. */
    private fun TestScope.loadedViewModel(): BookingViewModel = BookingViewModel(repository).also { testScheduler.advanceUntilIdle() }

    @Test
    fun the_menu_comes_from_the_catalogue_and_opens_on_its_first_treatment() = runTest(dispatcher) {
        val vm = loadedViewModel()

        val state = vm.state.value
        assertEquals(ServerCatalog.catalog.services, state.services)
        assertEquals(ServerCatalog.catalog.therapists, state.therapists)
        assertEquals(ServerCatalog.stone.id, state.serviceId)
        assertEquals("mai", state.therapistId)
        assertTrue(!state.catalogLoading && !state.catalogFailed)
    }

    @Test
    fun a_catalogue_that_cannot_be_read_offers_a_retry_rather_than_an_empty_menu() = runTest(dispatcher) {
        repository.fail = true
        val vm = loadedViewModel()

        assertTrue(vm.state.value.catalogFailed)
        assertTrue(vm.state.value.services.isEmpty())
        assertNull(vm.state.value.serviceId)

        repository.fail = false
        vm.onEvent(BookingEvent.RetryCatalog)
        testScheduler.advanceUntilIdle()

        assertEquals(2, repository.calls)
        assertTrue(!vm.state.value.catalogFailed)
        assertEquals(ServerCatalog.catalog.services, vm.state.value.services)
    }

    @Test
    fun applying_an_eligible_code_discounts_the_total() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.onEvent(BookingEvent.ServiceSelected(ServerCatalog.facial.id))
        vm.onEvent(BookingEvent.CodeChanged("jade20"))
        vm.onEvent(BookingEvent.ApplyCode)

        val state = vm.state.value
        assertIs<PromoCheck.Applied>(state.check)
        assertEquals(120 - 24, state.totalUsd)
    }

    @Test
    fun switching_treatment_rechecks_an_applied_code() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.onEvent(BookingEvent.ServiceSelected(ServerCatalog.facial.id))
        vm.onEvent(BookingEvent.CodeChanged("JADE20"))
        vm.onEvent(BookingEvent.ApplyCode)

        vm.onEvent(BookingEvent.ServiceSelected(ServerCatalog.stone.id))

        // The facial-only code must not keep discounting a massage.
        assertIs<PromoCheck.Rejected>(vm.state.value.check)
        assertEquals(165, vm.state.value.totalUsd)
    }

    @Test
    fun a_code_from_the_offers_screen_picks_a_treatment_it_applies_to() = runTest(dispatcher) {
        val vm = loadedViewModel()
        assertEquals(ServerCatalog.stone.id, vm.state.value.serviceId) // a massage by default

        vm.onEvent(BookingEvent.CodeOffered("jade20"))

        assertEquals(ServerCatalog.facial.id, vm.state.value.serviceId)
        assertIs<PromoCheck.Applied>(vm.state.value.check)
    }

    @Test
    fun a_code_offered_before_the_menu_arrives_is_applied_once_it_does() = runTest(dispatcher) {
        val vm = BookingViewModel(repository)

        vm.onEvent(BookingEvent.CodeOffered("jade20"))
        testScheduler.advanceUntilIdle()

        assertEquals(ServerCatalog.facial.id, vm.state.value.serviceId)
        assertIs<PromoCheck.Applied>(vm.state.value.check)
    }

    @Test
    fun editing_the_code_clears_the_previous_verdict() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.onEvent(BookingEvent.CodeChanged("JADE20"))
        vm.onEvent(BookingEvent.ApplyCode)
        vm.onEvent(BookingEvent.CodeChanged("JADE2"))

        assertNull(vm.state.value.check)
    }

    @Test
    fun choosing_another_day_drops_the_chosen_time() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.onEvent(BookingEvent.SlotSelected("2:30 PM"))
        vm.onEvent(BookingEvent.DaySelected(SpaCatalog.today.plusDays(4)))

        assertNull(vm.state.value.slot)
        assertEquals(SpaCatalog.today.plusDays(4), vm.state.value.day)
    }

    @Test
    fun a_request_needs_treatment_therapist_and_time() = runTest(dispatcher) {
        val vm = loadedViewModel()
        assertTrue(!vm.state.value.canRequest)

        vm.onEvent(BookingEvent.SlotSelected("4:00 PM"))

        assertTrue(vm.state.value.canRequest)
        vm.onEvent(BookingEvent.Submit)
        assertTrue(vm.state.value.requestSent)
    }

    @Test
    fun opening_notifications_closes_the_confirmation_and_navigates() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.onEvent(BookingEvent.SlotSelected("4:00 PM"))
        vm.onEvent(BookingEvent.Submit)

        vm.onEvent(BookingEvent.OpenNotifications)

        assertEquals(BookingEffect.NavigateToNotifications, vm.effects.first())
        assertTrue(!vm.state.value.requestSent)
    }
}
