package com.tana.spa.feature.booking

import com.tana.spa.feature.booking.domain.BookingAttempt
import com.tana.spa.feature.booking.domain.Slot
import com.tana.spa.feature.booking.presentation.ANY_THERAPIST
import com.tana.spa.feature.booking.presentation.BookingEffect
import com.tana.spa.feature.booking.presentation.BookingEvent
import com.tana.spa.feature.booking.presentation.BookingViewModel
import com.tana.spa.shared.booking.BookingErrorCodes
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
    private val catalog = FakeSpaCatalogRepository()
    private val bookings = FakeGuestBookingRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** The catalogue and the diary load on creation, so every test starts from a usable screen. */
    private fun TestScope.loadedViewModel(): BookingViewModel =
        BookingViewModel(catalog, bookings).also { testScheduler.advanceUntilIdle() }

    private fun BookingViewModel.chooseFirstFreeSlot() = onEvent(BookingEvent.SlotSelected(1_000))

    @Test
    fun the_menu_and_the_diary_come_from_the_server() = runTest(dispatcher) {
        val vm = loadedViewModel()

        val state = vm.state.value
        assertEquals(ServerCatalog.catalog.services, state.services)
        assertEquals(ServerCatalog.catalog.therapists, state.therapists)
        assertEquals(ServerCatalog.stone.id, state.serviceId)
        assertEquals(bookings.slots, state.slots)
        assertTrue(!state.catalogLoading && !state.catalogFailed && !state.slotsLoading)
    }

    @Test
    fun the_flow_opens_on_any_therapist_and_sends_no_therapist_with_it() = runTest(dispatcher) {
        val vm = loadedViewModel()
        assertEquals(ANY_THERAPIST, vm.state.value.therapistId)
        assertTrue(vm.state.value.anyTherapist)

        vm.chooseFirstFreeSlot()
        vm.onEvent(BookingEvent.Submit)
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(SentBooking(ServerCatalog.stone.id, null, 1_000, null)),
            bookings.sent.toList(),
            "the sample \"any\" id must never reach the server",
        )
        val day = SpaCatalog.today.plusDays(2).epochDay
        assertEquals(listOf(day to null, day to null), bookings.availabilityAsked.toList())
    }

    @Test
    fun choosing_a_therapist_asks_the_diary_again_for_their_own_times() = runTest(dispatcher) {
        val vm = loadedViewModel()

        vm.onEvent(BookingEvent.TherapistSelected("mai"))
        testScheduler.advanceUntilIdle()

        assertEquals("mai", bookings.availabilityAsked.last().second)

        vm.chooseFirstFreeSlot()
        vm.onEvent(BookingEvent.Submit)
        testScheduler.advanceUntilIdle()

        assertEquals("mai", bookings.sent.single().therapistSlug)
    }

    @Test
    fun a_booking_the_spa_took_is_confirmed_on_screen_with_the_therapist_it_assigned() = runTest(dispatcher) {
        bookings.createAnswer = BookingAttempt.Done(guestBooking(therapistName = "Lena Okafor"))
        val vm = loadedViewModel()
        vm.chooseFirstFreeSlot()

        vm.onEvent(BookingEvent.Submit)
        testScheduler.advanceUntilIdle()

        val sent = vm.state.value.sent
        assertEquals("Lena Okafor", sent?.therapistName)
        assertTrue(!vm.state.value.sending)
    }

    @Test
    fun each_refusal_reaches_the_screen_as_its_own_reason() = runTest(dispatcher) {
        val codes = listOf(
            BookingErrorCodes.SLOT_TAKEN,
            BookingErrorCodes.SLOT_IN_PAST,
            BookingErrorCodes.INVALID_CODE,
            BookingErrorCodes.UNKNOWN_SERVICE,
        )
        for (code in codes) {
            val vm = loadedViewModel()
            bookings.createAnswer = BookingAttempt.Refused(code)
            vm.chooseFirstFreeSlot()

            vm.onEvent(BookingEvent.Submit)
            testScheduler.advanceUntilIdle()

            assertEquals(BookingEffect.ShowRefusal(code), vm.effects.first())
            assertNull(vm.state.value.sent, "a refused request must not look confirmed")
        }
    }

    @Test
    fun a_request_that_never_arrives_is_said_differently() = runTest(dispatcher) {
        bookings.createAnswer = BookingAttempt.Offline
        val vm = loadedViewModel()
        vm.chooseFirstFreeSlot()

        vm.onEvent(BookingEvent.Submit)
        testScheduler.advanceUntilIdle()

        assertEquals(BookingEffect.ShowRefusal(null), vm.effects.first())
    }

    @Test
    fun a_time_the_diary_no_longer_offers_is_dropped() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.chooseFirstFreeSlot()
        assertEquals(1_000, vm.state.value.slotAtEpochMillis)

        bookings.slots = listOf(Slot(1_000, "9:00 AM", available = false), Slot(3_000, "11:30 AM", available = true))
        vm.onEvent(BookingEvent.DaySelected(SpaCatalog.today.plusDays(4)))
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.slotAtEpochMillis)
        assertTrue(!vm.state.value.canRequest)
    }

    @Test
    fun a_slow_answer_for_the_day_before_does_not_replace_the_times_on_offer() = runTest(dispatcher) {
        val vm = loadedViewModel()

        bookings.slots = listOf(Slot(9_000, "7:00 PM", available = true))
        bookings.slowAnswerMillis = 1_000
        vm.onEvent(BookingEvent.DaySelected(SpaCatalog.today.plusDays(3)))
        testScheduler.advanceTimeBy(1)

        bookings.slots = listOf(Slot(5_000, "1:00 PM", available = true))
        vm.onEvent(BookingEvent.DaySelected(SpaCatalog.today.plusDays(4)))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(5_000L), vm.state.value.slots.map { it.startAtEpochMillis }, "the day on screen is the day that answered last")
        assertTrue(!vm.state.value.slotsLoading)
    }

    @Test
    fun a_request_needs_a_treatment_and_a_time() = runTest(dispatcher) {
        val vm = loadedViewModel()
        assertTrue(!vm.state.value.canRequest)

        vm.chooseFirstFreeSlot()

        assertTrue(vm.state.value.canRequest)
    }

    @Test
    fun only_a_code_the_spa_accepted_is_sent_with_the_request() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.onEvent(BookingEvent.ServiceSelected(ServerCatalog.facial.id))
        vm.onEvent(BookingEvent.CodeChanged("jade20"))
        vm.chooseFirstFreeSlot()

        vm.onEvent(BookingEvent.Submit)
        testScheduler.advanceUntilIdle()
        assertNull(bookings.sent.single().code, "an unchecked code would make the server refuse the booking")

        bookings.sent.clear()
        vm.onEvent(BookingEvent.ApplyCode)
        vm.chooseFirstFreeSlot()
        vm.onEvent(BookingEvent.Submit)
        testScheduler.advanceUntilIdle()

        assertEquals("JADE20", bookings.sent.single().code)
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
        val vm = BookingViewModel(catalog, bookings)

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
    fun a_catalogue_that_cannot_be_read_offers_a_retry_rather_than_an_empty_menu() = runTest(dispatcher) {
        catalog.fail = true
        bookings.failAvailability = true
        val vm = loadedViewModel()

        assertTrue(vm.state.value.catalogFailed)
        assertTrue(vm.state.value.services.isEmpty())
        assertTrue(vm.state.value.slots.isEmpty())

        catalog.fail = false
        bookings.failAvailability = false
        vm.onEvent(BookingEvent.RetryCatalog)
        testScheduler.advanceUntilIdle()

        assertTrue(!vm.state.value.catalogFailed)
        assertEquals(ServerCatalog.catalog.services, vm.state.value.services)
        assertEquals(bookings.slots, vm.state.value.slots)
    }

    @Test
    fun opening_the_visits_list_closes_the_confirmation() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.chooseFirstFreeSlot()
        vm.onEvent(BookingEvent.Submit)
        testScheduler.advanceUntilIdle()

        vm.onEvent(BookingEvent.OpenBookings)

        assertEquals(BookingEffect.NavigateToBookings, vm.effects.first())
        assertNull(vm.state.value.sent)
    }

    @Test
    fun opening_notifications_closes_the_confirmation_and_navigates() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.chooseFirstFreeSlot()
        vm.onEvent(BookingEvent.Submit)
        testScheduler.advanceUntilIdle()

        vm.onEvent(BookingEvent.OpenNotifications)

        assertEquals(BookingEffect.NavigateToNotifications, vm.effects.first())
        assertNull(vm.state.value.sent)
    }
}
