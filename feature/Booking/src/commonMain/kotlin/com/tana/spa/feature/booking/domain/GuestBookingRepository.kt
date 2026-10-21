package com.tana.spa.feature.booking.domain

import com.tana.spa.shared.booking.BookingRules
import com.tana.spa.shared.booking.BookingStatusDto
import com.tana.spa.shared.booking.DeclineReasonDto

/**
 * One open time in the diary, as the server offers it. [startAtEpochMillis] is what a booking
 * names; [startHour] is the same moment as an hour of the house's day (14.5 for half past two),
 * which is what a screen words in the reader's language.
 */
data class Slot(val startAtEpochMillis: Long, val startHour: Double, val available: Boolean)

/**
 * A booking the signed-in guest made. [startDayEpochDay] and [startHour] are the start in the
 * spa's own time zone, so a guest travelling abroad still reads the time they will turn up at.
 */
data class GuestBooking(
    val id: String,
    val reference: String,
    val serviceName: String,
    val minutes: Int,
    val therapistName: String,
    val startAtEpochMillis: Long,
    val startDayEpochDay: Int,
    val startHour: Double,
    val status: BookingStatusDto,
    val total: Int,
    val discount: Int,
    val code: String?,
    val declineReason: DeclineReasonDto?,
    val alternativeOffer: String?,
) {
    /**
     * The spa keeps the hour once it is this close, so the app withholds the action and says to
     * phone instead. The server refuses it either way; this only avoids offering a dead button.
     */
    fun cancellable(nowEpochMillis: Long): Boolean = status in CANCELLABLE && startAtEpochMillis - nowEpochMillis >= CANCEL_WINDOW_MILLIS

    /** True once it is too late to cancel but the visit is still ahead: the guest has to call. */
    fun cancelTooLate(nowEpochMillis: Long): Boolean =
        status in CANCELLABLE && !cancellable(nowEpochMillis) && startAtEpochMillis > nowEpochMillis

    /**
     * Whether the visit is happening right now. The spa has no status for it and does not need
     * one: the hour and the treatment's length already say when it runs, and a status would only
     * be as fresh as the last time someone at the desk remembered to set it.
     */
    fun inProgress(nowEpochMillis: Long): Boolean = status == BookingStatusDto.CONFIRMED &&
        nowEpochMillis >= startAtEpochMillis &&
        nowEpochMillis < startAtEpochMillis + minutes * MILLIS_PER_MINUTE

    private companion object {
        val CANCELLABLE = setOf(BookingStatusDto.PENDING, BookingStatusDto.CONFIRMED)
        const val MILLIS_PER_MINUTE = 60_000L
        const val CANCEL_WINDOW_MILLIS = BookingRules.CANCEL_WINDOW_MINUTES * MILLIS_PER_MINUTE
    }
}

/**
 * What came back from a write. [Refused] carries the server's own code (one of
 * `BookingErrorCodes`) so the screen can word the reason; [Offline] is nothing having reached the
 * spa at all, which is worth saying differently.
 */
sealed interface BookingAttempt {
    data class Done(val booking: GuestBooking) : BookingAttempt
    data class Refused(val code: String?) : BookingAttempt
    data object Offline : BookingAttempt
}

interface GuestBookingRepository {
    /**
     * The open times of [dayEpochDay] for [serviceSlug], whose length decides how much of the
     * diary a slot needs. A null [therapistSlug] asks for "any therapist".
     */
    suspend fun availability(dayEpochDay: Int, therapistSlug: String?, serviceSlug: String?): List<Slot>

    /** A null [therapistSlug] lets the spa assign whoever is free. */
    suspend fun create(serviceSlug: String, therapistSlug: String?, startAtEpochMillis: Long, code: String?): BookingAttempt

    /** The signed-in guest's own bookings, newest visit first. Throws when it cannot be read. */
    suspend fun mine(): List<GuestBooking>

    suspend fun cancel(bookingId: String): BookingAttempt
}
