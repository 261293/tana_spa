package com.tana.spa.feature.booking.presentation

import com.tana.spa.feature.booking.domain.GuestBooking
import com.tana.spa.feature.booking.domain.Slot
import com.tana.spa.shared.spa.PromoCheck
import com.tana.spa.shared.spa.Service
import com.tana.spa.shared.spa.SpaDate
import com.tana.spa.shared.spa.Therapist
import kotlin.time.Clock

/** The picker's "whoever is free" choice. It is not a therapist, so it has no slug of its own. */
const val ANY_THERAPIST = "any"

/**
 * [check] is null until the guest applies a code; a null that follows an edit means "not checked
 * yet", which the screen words differently from a refusal. [services] and [therapists] come from
 * the server's catalogue, and [slots] from its diary, so everything this screen sends names
 * something the server knows.
 */
data class BookingState(
    val services: List<Service> = emptyList(),
    val therapists: List<Therapist> = emptyList(),
    val catalogLoading: Boolean = true,
    val catalogFailed: Boolean = false,
    val serviceId: String? = null,
    /** [ANY_THERAPIST] until the guest names one; the request then carries no therapist. */
    val therapistId: String = ANY_THERAPIST,
    /** The spa's own day, from the catalogue; the device's until it answers. */
    val todayEpochDay: Int = deviceToday(),
    /** Null until the guest picks one, so the offered day follows [today] when it is corrected. */
    val chosenDay: SpaDate? = null,
    val slots: List<Slot> = emptyList(),
    val slotsLoading: Boolean = true,
    val slotAtEpochMillis: Long? = null,
    val code: String = "",
    val check: PromoCheck? = null,
    val sending: Boolean = false,
    /** The booking the spa recorded; the confirmation sheet is open while it is set. */
    val sent: GuestBooking? = null,
) {
    val today: SpaDate get() = SpaDate.fromEpochDay(todayEpochDay)
    val day: SpaDate get() = chosenDay ?: today.plusDays(DEFAULT_DAYS_AHEAD)
    val service: Service? get() = services.firstOrNull { it.id == serviceId }
    val therapist: Therapist? get() = therapists.firstOrNull { it.id == therapistId }
    val anyTherapist: Boolean get() = therapistId == ANY_THERAPIST
    val slot: Slot? get() = slots.firstOrNull { it.startAtEpochMillis == slotAtEpochMillis }
    val discountUsd: Int get() = (check as? PromoCheck.Applied)?.discountUsd ?: 0
    val totalUsd: Int get() = (service?.priceUsd ?: 0) - discountUsd
    val canRequest: Boolean get() = service != null && slot != null && !sending

    private companion object {
        /** The spa asks for a little notice, so the screen opens on a day it can usually take. */
        const val DEFAULT_DAYS_AHEAD = 2
    }
}

/**
 * What the screen counts from before the catalogue names the spa's own day. UTC, not the
 * device's zone, so it can be a day out until the catalogue answers and corrects it.
 */
private fun deviceToday(): Int = (Clock.System.now().toEpochMilliseconds() / MILLIS_PER_DAY).toInt()

private const val MILLIS_PER_DAY = 86_400_000L

sealed interface BookingEvent {
    /** A code carried in from the offers screen: it also picks a treatment it is valid for. */
    data class CodeOffered(val code: String) : BookingEvent
    data class ServiceSelected(val id: String) : BookingEvent
    data class TherapistSelected(val id: String) : BookingEvent
    data class DaySelected(val day: SpaDate) : BookingEvent
    data class SlotSelected(val startAtEpochMillis: Long) : BookingEvent
    data class CodeChanged(val value: String) : BookingEvent
    data object ApplyCode : BookingEvent
    data object RetryCatalog : BookingEvent
    data object Submit : BookingEvent
    data object DismissSent : BookingEvent
    data object OpenNotifications : BookingEvent
    data object OpenBookings : BookingEvent
}

sealed interface BookingEffect {
    data object NavigateToNotifications : BookingEffect
    data object NavigateToBookings : BookingEffect

    /** The spa refused the request; [code] is one of `BookingErrorCodes`, null when nothing arrived. */
    data class ShowRefusal(val code: String?) : BookingEffect
}
