package com.tana.spa.feature.admin.presentation

import com.tana.spa.shared.spa.BookingRequest
import com.tana.spa.shared.spa.RequestStatus
import com.tana.spa.shared.spa.SpaCatalog

enum class DeclineReason { OVERLAP, THERAPIST_UNAVAILABLE, NO_ROOM }

enum class AlternativeOffer { NONE, TODAY_MIDDAY, TODAY_WITH_MAI, TOMORROW_MORNING }

/** [declining] non-null is the open decline dialog, carrying the request it will refuse. */
data class AdminRequestsState(
    val requests: List<BookingRequest> = SpaCatalog.requests,
    val selectedId: String = SpaCatalog.requests.first().id,
    val declining: BookingRequest? = null,
    val reason: DeclineReason = DeclineReason.THERAPIST_UNAVAILABLE,
    val alternative: AlternativeOffer = AlternativeOffer.NONE,
) {
    val selected: BookingRequest? get() = requests.firstOrNull { it.id == selectedId }
    val pending: List<BookingRequest> get() = requests.filter { it.status == RequestStatus.PENDING }
    val confirmed: List<BookingRequest> get() = requests.filter { it.status == RequestStatus.CONFIRMED }
}

sealed interface AdminRequestsEvent {
    data class RequestSelected(val id: String) : AdminRequestsEvent
    data class Confirmed(val id: String) : AdminRequestsEvent
    data class DeclineStarted(val id: String) : AdminRequestsEvent
    data class ReasonChosen(val reason: DeclineReason) : AdminRequestsEvent
    data class AlternativeChosen(val alternative: AlternativeOffer) : AdminRequestsEvent
    data object DeclineConfirmed : AdminRequestsEvent
    data object DeclineDismissed : AdminRequestsEvent
}

sealed interface AdminRequestsEffect {
    data class ShowConfirmed(val guest: String) : AdminRequestsEffect
    data class ShowDeclined(val guest: String, val withAlternative: Boolean) : AdminRequestsEffect
}
