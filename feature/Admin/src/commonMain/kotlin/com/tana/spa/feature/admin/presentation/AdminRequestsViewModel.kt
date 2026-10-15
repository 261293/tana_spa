package com.tana.spa.feature.admin.presentation

import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.shared.spa.RequestStatus

/**
 * The front-desk queue. Decisions are kept in memory until the booking endpoints exist; the guest
 * notification the copy promises is sent by the server, not here.
 */
class AdminRequestsViewModel : MviViewModel<AdminRequestsState, AdminRequestsEvent, AdminRequestsEffect>(AdminRequestsState()) {

    override fun handleEvent(event: AdminRequestsEvent) {
        when (event) {
            is AdminRequestsEvent.RequestSelected -> setState { copy(selectedId = event.id) }

            is AdminRequestsEvent.Confirmed -> {
                val request = state.value.requests.firstOrNull { it.id == event.id }
                AppLog.i(logTag) { "request ${event.id} confirmed" }
                setState {
                    val updated = withStatus(event.id, RequestStatus.CONFIRMED)
                    // Move on to the next one still waiting, the way the queue is worked.
                    val next = updated.firstOrNull { it.status == RequestStatus.PENDING && it.id != event.id }
                    copy(requests = updated, selectedId = next?.id ?: selectedId)
                }
                if (request != null) {
                    sendEffect(AdminRequestsEffect.ShowConfirmed(request.guest))
                }
            }

            is AdminRequestsEvent.DeclineStarted -> setState {
                val request = requests.firstOrNull { it.id == event.id }
                copy(
                    declining = request,
                    reason = if (request?.conflict != null) DeclineReason.OVERLAP else DeclineReason.THERAPIST_UNAVAILABLE,
                    alternative = AlternativeOffer.NONE,
                )
            }

            is AdminRequestsEvent.ReasonChosen -> setState { copy(reason = event.reason) }

            is AdminRequestsEvent.AlternativeChosen -> setState { copy(alternative = event.alternative) }

            AdminRequestsEvent.DeclineConfirmed -> {
                val declining = state.value.declining
                val withAlternative = state.value.alternative != AlternativeOffer.NONE
                if (declining != null) {
                    AppLog.i(logTag) { "request ${declining.id} declined (${state.value.reason})" }
                    setState { copy(requests = withStatus(declining.id, RequestStatus.DECLINED), declining = null) }
                    sendEffect(AdminRequestsEffect.ShowDeclined(declining.guest, withAlternative))
                } else {
                    setState { copy(declining = null) }
                }
            }

            AdminRequestsEvent.DeclineDismissed -> setState { copy(declining = null) }
        }
    }

    private fun AdminRequestsState.withStatus(id: String, status: RequestStatus) =
        requests.map { if (it.id == id) it.copy(status = status) else it }
}
