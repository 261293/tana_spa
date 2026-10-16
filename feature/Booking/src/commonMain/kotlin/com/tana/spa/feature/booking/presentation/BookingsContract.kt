package com.tana.spa.feature.booking.presentation

import com.tana.spa.feature.booking.domain.GuestBooking

/**
 * [cancelling] non-null is the open confirmation: cancelling is not something to do by accident.
 * [openedId] is the visit the guest arrived for, from a reminder or a decision in the notification
 * centre; its row is picked out so the one the notification was about is obvious.
 */
data class BookingsState(
    val bookings: List<GuestBooking> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val working: Boolean = false,
    val nowEpochMillis: Long = 0,
    val cancelling: GuestBooking? = null,
    val openedId: String? = null,
)

sealed interface BookingsEvent {
    data object Refresh : BookingsEvent

    /** Arrived from a notification about [bookingId]; null is the screen opened on its own. */
    data class Opened(val bookingId: String?) : BookingsEvent
    data class CancelStarted(val id: String) : BookingsEvent
    data object CancelConfirmed : BookingsEvent
    data object CancelDismissed : BookingsEvent
}

sealed interface BookingsEffect {
    data object ShowCancelled : BookingsEffect

    /** The spa refused it; [code] is one of `BookingErrorCodes`, null when nothing arrived. */
    data class ShowRefusal(val code: String?) : BookingsEffect
}
