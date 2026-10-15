package com.tana.spa.feature.booking.presentation

import com.tana.spa.shared.spa.Service

/**
 * [notFound] is a catalogue that was read but does not hold this treatment, which the screen words
 * differently from [failed], a catalogue that could not be read at all.
 */
data class ServiceDetailState(
    val serviceId: String? = null,
    val loading: Boolean = true,
    val service: Service? = null,
    val notFound: Boolean = false,
    val failed: Boolean = false,
)

sealed interface ServiceDetailEvent {
    /** A null [serviceId] is a route without its argument, which reads as not found. */
    data class Opened(val serviceId: String?) : ServiceDetailEvent
    data object Retry : ServiceDetailEvent
    data object Book : ServiceDetailEvent
}

sealed interface ServiceDetailEffect {
    /** The guest is done reading; the booking flow is one step back, with this treatment chosen. */
    data object ReturnToBooking : ServiceDetailEffect
}
