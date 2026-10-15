package com.tana.spa.feature.admin.presentation

import com.tana.spa.feature.admin.domain.DeskRequest
import com.tana.spa.feature.admin.domain.DeskStatus
import com.tana.spa.shared.booking.DeclineReasonDto

enum class AlternativeOffer { NONE, TODAY_MIDDAY, TODAY_WITH_MAI, TOMORROW_MORNING }

/**
 * [declining] non-null is the open decline dialog, carrying the request it will refuse.
 * [requests] is the owner's queue and today's diary in one list, as the server read them.
 */
data class AdminRequestsState(
    val requests: List<DeskRequest> = emptyList(),
    val selectedId: String? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val working: Boolean = false,
    val nowEpochMillis: Long = 0,
    /** The day the queue was read for, in the house's zone; the header dates the screen with it. */
    val dayEpochDay: Int = 0,
    val declining: DeskRequest? = null,
    val reason: DeclineReasonDto = DeclineReasonDto.THERAPIST_UNAVAILABLE,
    val alternative: AlternativeOffer = AlternativeOffer.NONE,
) {
    val selected: DeskRequest? get() = requests.firstOrNull { it.id == selectedId }
    val pending: List<DeskRequest> get() = requests.filter { it.status == DeskStatus.PENDING }
    val confirmed: List<DeskRequest> get() = requests.filter { it.status == DeskStatus.CONFIRMED }
    val stale: List<DeskRequest> get() = pending.filter { it.stale }
}

sealed interface AdminRequestsEvent {
    data object Refresh : AdminRequestsEvent
    data class RequestSelected(val id: String) : AdminRequestsEvent

    /** [force] is the second press, on a request the server refused as an overlap. */
    data class Confirmed(val id: String, val force: Boolean = false) : AdminRequestsEvent
    data class DeclineStarted(val id: String) : AdminRequestsEvent
    data class ReasonChosen(val reason: DeclineReasonDto) : AdminRequestsEvent
    data class AlternativeChosen(val alternative: AlternativeOffer) : AdminRequestsEvent
    data object DeclineConfirmed : AdminRequestsEvent
    data object DeclineDismissed : AdminRequestsEvent
    data class VisitRecorded(val id: String, val attended: Boolean) : AdminRequestsEvent
}

sealed interface AdminRequestsEffect {
    data class ShowConfirmed(val guest: String) : AdminRequestsEffect
    data class ShowDeclined(val guest: String, val withAlternative: Boolean) : AdminRequestsEffect
    data class ShowVisitRecorded(val guest: String, val attended: Boolean) : AdminRequestsEffect

    /** The server refused the action; [overlap] asks the owner to confirm the clash deliberately. */
    data class ShowRefused(val overlap: Boolean) : AdminRequestsEffect
}
