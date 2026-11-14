package com.tana.spa.feature.booking

import com.tana.spa.feature.booking.presentation.BookingsState
import com.tana.spa.shared.booking.BookingStatusDto
import kotlin.test.Test
import kotlin.test.assertEquals

class BookingsStateTest {
    private val state = BookingsState(
        nowEpochMillis = 10_000,
        bookings = listOf(
            guestBooking(id = "later", status = BookingStatusDto.CONFIRMED, startAtEpochMillis = 90_000),
            guestBooking(id = "soon", status = BookingStatusDto.PENDING, startAtEpochMillis = 20_000),
            guestBooking(id = "cancelled", status = BookingStatusDto.CANCELLED, startAtEpochMillis = 50_000),
            guestBooking(id = "done", status = BookingStatusDto.COMPLETED, startAtEpochMillis = 2_000),
            guestBooking(id = "lapsed", status = BookingStatusDto.CONFIRMED, startAtEpochMillis = 5_000),
        ),
    )

    @Test
    fun upcoming_holds_only_live_visits_ahead_soonest_first() {
        assertEquals(listOf("soon", "later"), state.upcoming.map { it.id })
    }

    @Test
    fun past_holds_everything_else_newest_first() {
        assertEquals(listOf("cancelled", "lapsed", "done"), state.past.map { it.id })
    }
}
