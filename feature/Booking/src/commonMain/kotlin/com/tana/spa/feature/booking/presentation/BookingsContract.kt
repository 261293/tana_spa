package com.tana.spa.feature.booking.presentation

import com.tana.spa.feature.booking.domain.GuestBooking

/** [cancelling] non-null is the open confirmation: cancelling is not something to do by accident. */
data class BookingsState(
    val bookings: List<GuestBooking> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val working: Boolean = false,
    val nowEpochMillis: Long = 0,
    val cancelling: GuestBooking? = null,
)

sealed interface BookingsEvent {
    data object Refresh : BookingsEvent
    data class CancelStarted(val id: String) : BookingsEvent
    data object CancelConfirmed : BookingsEvent
    data object CancelDismissed : BookingsEvent
}

sealed interface BookingsEffect {
    data object ShowCancelled : BookingsEffect

    /** The spa refused it; [code] is one of `BookingErrorCodes`, null when nothing arrived. */
    data class ShowRefusal(val code: String?) : BookingsEffect
}
