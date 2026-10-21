package com.tana.spa.feature.booking.domain

import com.tana.spa.shared.booking.BookingStatusDto

/** The three states a visit that goes to plan passes through, in the order it passes through them. */
enum class VisitStep { REQUESTED, CONFIRMED, COMPLETED }

/**
 * How far along the visit is, or null when it ended off that track.
 *
 * Declined, cancelled and no-show are outcomes, not positions: drawing them as a step would put a
 * visit that never happened somewhere on the way to happening. A caller that gets null says what
 * became of the visit instead.
 */
fun visitStep(status: BookingStatusDto): VisitStep? = when (status) {
    BookingStatusDto.PENDING -> VisitStep.REQUESTED
    BookingStatusDto.CONFIRMED -> VisitStep.CONFIRMED
    BookingStatusDto.COMPLETED -> VisitStep.COMPLETED
    BookingStatusDto.DECLINED, BookingStatusDto.CANCELLED, BookingStatusDto.NO_SHOW -> null
}

/**
 * The one visit worth keeping in front of the guest wherever they are in the app: the one running
 * now, otherwise the soonest still ahead within [UPCOMING_WINDOW_HOURS].
 *
 * The window is what keeps the strip meaningful. A visit three weeks out is a diary entry, and a
 * strip that is always there is one nobody reads.
 */
fun List<GuestBooking>.currentVisit(nowEpochMillis: Long): GuestBooking? = firstOrNull { it.inProgress(nowEpochMillis) }
    ?: filter { it.status in FOLLOWABLE && it.startsWithin(nowEpochMillis) }
        .minByOrNull { it.startAtEpochMillis }

private fun GuestBooking.startsWithin(nowEpochMillis: Long): Boolean {
    val away = startAtEpochMillis - nowEpochMillis
    return away > 0 && away <= UPCOMING_WINDOW_MILLIS
}

private val FOLLOWABLE = setOf(BookingStatusDto.PENDING, BookingStatusDto.CONFIRMED)
private const val UPCOMING_WINDOW_HOURS = 24
private const val MILLIS_PER_HOUR = 3_600_000L
private const val UPCOMING_WINDOW_MILLIS = UPCOMING_WINDOW_HOURS * MILLIS_PER_HOUR
