package com.tana.spa.feature.booking.presentation

import com.tana.spa.shared.spa.PromoCheck
import com.tana.spa.shared.spa.Service
import com.tana.spa.shared.spa.SpaCatalog
import com.tana.spa.shared.spa.SpaDate
import com.tana.spa.shared.spa.Therapist

/**
 * [check] is null until the guest applies a code; a null that follows an edit means "not checked
 * yet", which the screen words differently from a refusal.
 */
data class BookingState(
    val serviceId: String? = null,
    val therapistId: String? = null,
    val day: SpaDate = SpaCatalog.today.plusDays(2),
    val slot: String? = null,
    val code: String = "",
    val check: PromoCheck? = null,
    val requestSent: Boolean = false,
) {
    val service: Service? get() = serviceId?.let { SpaCatalog.service(it) }
    val therapist: Therapist? get() = therapistId?.let { SpaCatalog.therapist(it) }
    val discountUsd: Int get() = (check as? PromoCheck.Applied)?.discountUsd ?: 0
    val totalUsd: Int get() = (service?.priceUsd ?: 0) - discountUsd
    val canRequest: Boolean get() = service != null && therapist != null && slot != null
}

sealed interface BookingEvent {
    /** A code carried in from the offers screen: it also picks a treatment it is valid for. */
    data class CodeOffered(val code: String) : BookingEvent
    data class ServiceSelected(val id: String) : BookingEvent
    data class TherapistSelected(val id: String) : BookingEvent
    data class DaySelected(val day: SpaDate) : BookingEvent
    data class SlotSelected(val slot: String) : BookingEvent
    data class CodeChanged(val value: String) : BookingEvent
    data object ApplyCode : BookingEvent
    data object Submit : BookingEvent
    data object DismissSent : BookingEvent
    data object OpenNotifications : BookingEvent
}

sealed interface BookingEffect {
    data object NavigateToNotifications : BookingEffect
}
