package com.tana.spa.feature.booking

import com.tana.spa.feature.booking.domain.BookingAttempt
import com.tana.spa.feature.booking.presentation.BookingsEffect
import com.tana.spa.feature.booking.presentation.BookingsEvent
import com.tana.spa.feature.booking.presentation.BookingsViewModel
import com.tana.spa.shared.booking.BookingErrorCodes
import com.tana.spa.shared.booking.BookingRules
import com.tana.spa.shared.booking.BookingStatusDto
import com.tana.spa.shared.booking.DeclineReasonDto
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val bookings = FakeGuestBookingRepository()

    private val hour = 60L * 60_000
    private val cancelWindow = BookingRules.CANCEL_WINDOW_MINUTES * 60_000L

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): BookingsViewModel = BookingsViewModel(bookings).also { testScheduler.advanceUntilIdle() }

    @Test
    fun the_list_comes_from_the_server_with_its_statuses() = runTest(dispatcher) {
        bookings.mine = listOf(
            guestBooking("a"),
            guestBooking("b", status = BookingStatusDto.DECLINED, declineReason = DeclineReasonDto.NO_ROOM),
        )

        val state = viewModel().state.value

        assertEquals(listOf("a", "b"), state.bookings.map { it.id })
        assertEquals(DeclineReasonDto.NO_ROOM, state.bookings[1].declineReason)
        assertFalse(state.loading)
        assertFalse(state.failed)
    }

    @Test
    fun arriving_from_a_notification_picks_out_that_visit_and_reads_the_list_again() = runTest(dispatcher) {
        bookings.mine = listOf(guestBooking("a"), guestBooking("b"))
        val vm = viewModel()
        val reads = bookings.mineCalls

        vm.onEvent(BookingsEvent.Opened(null))
        testScheduler.advanceUntilIdle()
        assertNull(vm.state.value.openedId, "the screen was opened on its own")
        assertEquals(reads, bookings.mineCalls, "there is nothing to catch up with")

        vm.onEvent(BookingsEvent.Opened("b"))
        testScheduler.advanceUntilIdle()

        assertEquals("b", vm.state.value.openedId)
        assertEquals(reads + 1, bookings.mineCalls, "a notification can be older than the list in hand")
    }

    @Test
    fun a_list_that_cannot_be_read_offers_a_retry() = runTest(dispatcher) {
        bookings.failMine = true
        val vm = viewModel()
        assertTrue(vm.state.value.failed)

        bookings.failMine = false
        bookings.mine = listOf(guestBooking("a"))
        vm.onEvent(BookingsEvent.Refresh)
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.failed)
        assertEquals(listOf("a"), vm.state.value.bookings.map { it.id })
    }

    @Test
    fun cancelling_is_offered_until_two_hours_before_the_start() {
        val start = 100 * hour
        val booking = guestBooking(startAtEpochMillis = start)

        assertTrue(booking.cancellable(start - cancelWindow))
        assertFalse(booking.cancellable(start - cancelWindow + 1))
        assertTrue(booking.cancelTooLate(start - hour), "still ahead, but the spa keeps the hour")
        assertFalse(booking.cancelTooLate(start + hour), "a visit in the past is not a cancellation")
        assertFalse(
            guestBooking(status = BookingStatusDto.DECLINED, startAtEpochMillis = start).cancellable(0),
            "there is nothing to call off",
        )
    }

    @Test
    fun cancelling_asks_first_and_then_tells_the_server() = runTest(dispatcher) {
        bookings.mine = listOf(guestBooking("a"))
        bookings.cancelAnswer = BookingAttempt.Done(guestBooking("a", status = BookingStatusDto.CANCELLED))
        val vm = viewModel()

        vm.onEvent(BookingsEvent.CancelStarted("a"))
        assertEquals("a", vm.state.value.cancelling?.id)
        assertTrue(bookings.cancelled.isEmpty(), "the dialog alone must not cancel anything")

        vm.onEvent(BookingsEvent.CancelConfirmed)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("a"), bookings.cancelled.toList())
        assertEquals(BookingStatusDto.CANCELLED, vm.state.value.bookings.single().status)
        assertEquals(BookingsEffect.ShowCancelled, vm.effects.first())
        assertNull(vm.state.value.cancelling)
    }

    @Test
    fun keeping_the_booking_closes_the_question_without_sending_anything() = runTest(dispatcher) {
        bookings.mine = listOf(guestBooking("a"))
        val vm = viewModel()

        vm.onEvent(BookingsEvent.CancelStarted("a"))
        vm.onEvent(BookingsEvent.CancelDismissed)
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.cancelling)
        assertTrue(bookings.cancelled.isEmpty())
    }

    @Test
    fun a_refused_cancellation_is_explained_and_the_list_is_read_again() = runTest(dispatcher) {
        bookings.mine = listOf(guestBooking("a"))
        bookings.cancelAnswer = BookingAttempt.Refused(BookingErrorCodes.TOO_LATE_TO_CANCEL)
        val vm = viewModel()

        vm.onEvent(BookingsEvent.CancelStarted("a"))
        vm.onEvent(BookingsEvent.CancelConfirmed)
        testScheduler.advanceUntilIdle()

        assertEquals(BookingsEffect.ShowRefusal(BookingErrorCodes.TOO_LATE_TO_CANCEL), vm.effects.first())
        assertEquals(BookingStatusDto.PENDING, vm.state.value.bookings.single().status)
        assertFalse(vm.state.value.working)
    }

    @Test
    fun tapping_a_visit_opens_it_and_tapping_the_open_one_folds_it_away() = runTest(dispatcher) {
        bookings.mine = listOf(guestBooking("a"), guestBooking("b"))
        val vm = viewModel()

        vm.onEvent(BookingsEvent.RowToggled("a"))
        assertEquals("a", vm.state.value.openedId)

        vm.onEvent(BookingsEvent.RowToggled("b"))
        assertEquals("b", vm.state.value.openedId)

        vm.onEvent(BookingsEvent.RowToggled("b"))
        assertNull(vm.state.value.openedId)
    }
}
