package com.tana.spa.feature.booking.domain

import com.tana.spa.shared.booking.BookingStatusDto

enum class VisitStep { REQUESTED, CONFIRMED, COMPLETED }

fun visitStep(status: BookingStatusDto): VisitStep? = when (status) {
    BookingStatusDto.PENDING -> VisitStep.REQUESTED
    BookingStatusDto.CONFIRMED -> VisitStep.CONFIRMED
    BookingStatusDto.COMPLETED -> VisitStep.COMPLETED
    BookingStatusDto.DECLINED, BookingStatusDto.CANCELLED, BookingStatusDto.NO_SHOW -> null
}

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
