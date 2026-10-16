package com.tana.spa.feature.booking

import com.tana.spa.feature.booking.domain.BookingAttempt
import com.tana.spa.feature.booking.domain.GuestBooking
import com.tana.spa.feature.booking.domain.GuestBookingRepository
import com.tana.spa.feature.booking.domain.Slot
import com.tana.spa.shared.booking.BookingStatusDto
import com.tana.spa.shared.booking.DeclineReasonDto
import kotlinx.coroutines.delay

/** A request the screen sent, so a test can assert on the slug and the time, not on call counts. */
data class SentBooking(val serviceSlug: String, val therapistSlug: String?, val startAtEpochMillis: Long, val code: String?)

fun guestBooking(
    id: String = "b1",
    status: BookingStatusDto = BookingStatusDto.PENDING,
    startAtEpochMillis: Long = 4_000,
    therapistName: String = "Mai Tran",
    declineReason: DeclineReasonDto? = null,
    alternativeOffer: String? = null,
) = GuestBooking(
    id = id,
    reference = "BK-$id",
    serviceName = "Warm Stone Massage",
    minutes = 90,
    therapistName = therapistName,
    startAtEpochMillis = startAtEpochMillis,
    startLabel = "Wed Oct 21, 2:30 PM",
    status = status,
    total = 165,
    discount = 0,
    code = null,
    declineReason = declineReason,
    alternativeOffer = alternativeOffer,
)

class FakeGuestBookingRepository(
    var slots: List<Slot> = listOf(
        Slot(1_000, "9:00 AM", available = true),
        Slot(2_000, "10:00 AM", available = false),
        Slot(3_000, "11:30 AM", available = true),
    ),
) : GuestBookingRepository {
    var mine: List<GuestBooking> = emptyList()
    var mineCalls = 0
    var createAnswer: BookingAttempt = BookingAttempt.Done(guestBooking())
    var cancelAnswer: BookingAttempt = BookingAttempt.Done(guestBooking(status = BookingStatusDto.CANCELLED))
    var failAvailability = false
    var failMine = false

    /** Holds up the next availability answer, so an earlier call can answer after a later one. */
    var slowAnswerMillis = 0L

    val sent = mutableListOf<SentBooking>()
    val cancelled = mutableListOf<String>()

    /** Day, therapist and treatment of every diary request, in order. */
    val availabilityAsked = mutableListOf<Triple<Int, String?, String?>>()

    override suspend fun availability(dayEpochDay: Int, therapistSlug: String?, serviceSlug: String?): List<Slot> {
        availabilityAsked += Triple(dayEpochDay, therapistSlug, serviceSlug)
        if (failAvailability) throw IllegalStateException("offline")
        val answer = slots
        delay(slowAnswerMillis.also { slowAnswerMillis = 0 })
        return answer
    }

    override suspend fun create(serviceSlug: String, therapistSlug: String?, startAtEpochMillis: Long, code: String?): BookingAttempt {
        sent += SentBooking(serviceSlug, therapistSlug, startAtEpochMillis, code)
        return createAnswer
    }

    override suspend fun mine(): List<GuestBooking> {
        mineCalls++
        if (failMine) throw IllegalStateException("offline")
        return mine
    }

    override suspend fun cancel(bookingId: String): BookingAttempt {
        cancelled += bookingId
        return cancelAnswer
    }
}
