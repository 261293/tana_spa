package com.tana.spa.feature.admin.presentation

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.admin.domain.DeskRefused
import com.tana.spa.feature.admin.domain.DeskRepository
import com.tana.spa.feature.admin.domain.DeskRequest
import com.tana.spa.feature.admin.domain.DeskStatus
import com.tana.spa.shared.booking.BookingErrorCodes
import com.tana.spa.shared.booking.DeclineReasonDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * The owner's queue, read from the server. Every decision is a call: the list is reloaded from
 * the answer rather than edited locally, so what the desk sees is what the spa's records say.
 */
class AdminRequestsViewModel(private val desk: DeskRepository) :
    MviViewModel<AdminRequestsState, AdminRequestsEvent, AdminRequestsEffect>(AdminRequestsState()) {

    init {
        refresh()
    }

    override fun handleEvent(event: AdminRequestsEvent) {
        when (event) {
            AdminRequestsEvent.Refresh -> refresh()

            is AdminRequestsEvent.RequestSelected -> setState { copy(selectedId = event.id) }

            is AdminRequestsEvent.Confirmed -> decide(event.id) { desk.confirm(event.id, event.force) }

            is AdminRequestsEvent.DeclineStarted -> setState {
                val request = requests.firstOrNull { it.id == event.id }
                copy(
                    declining = request,
                    reason = if (request?.conflict != null) DeclineReasonDto.OVERLAP else DeclineReasonDto.THERAPIST_UNAVAILABLE,
                    alternative = AlternativeOffer.NONE,
                )
            }

            is AdminRequestsEvent.ReasonChosen -> setState { copy(reason = event.reason) }

            is AdminRequestsEvent.AlternativeChosen -> setState { copy(alternative = event.alternative) }

            AdminRequestsEvent.DeclineConfirmed -> declineSelected()

            AdminRequestsEvent.DeclineDismissed -> setState { copy(declining = null) }

            is AdminRequestsEvent.VisitRecorded -> decide(event.id) { desk.recordVisit(event.id, event.attended) }
        }
    }

    private fun refresh() {
        setState { copy(loading = true, failed = false) }
        viewModelScope.launch {
            runCatchingCancellable { desk.queue() }
                .onSuccess { queue ->
                    val requests = (queue.pending + queue.scheduled).distinctBy { it.id }
                    AppLog.i(logTag) { "desk queue: ${queue.pending.size} waiting, ${queue.scheduled.size} on the day" }
                    setState {
                        copy(
                            requests = requests,
                            selectedId = selectedId?.takeIf { id -> requests.any { it.id == id } }
                                ?: requests.firstOrNull { it.status == DeskStatus.PENDING }?.id
                                ?: requests.firstOrNull()?.id,
                            loading = false,
                            failed = false,
                            nowEpochMillis = Clock.System.now().toEpochMilliseconds(),
                            dayEpochDay = queue.dayEpochDay,
                        )
                    }
                }
                .onFailure { setState { copy(loading = false, failed = true) } }
        }
    }

    /** Applies the server's answer to the one row it decided, then moves on to the next waiting. */
    private fun decide(id: String, call: suspend () -> DeskRequest) {
        if (state.value.working) return
        val guest = state.value.requests.firstOrNull { it.id == id }?.guest.orEmpty()
        setState { copy(working = true) }
        viewModelScope.launch {
            runCatchingCancellable { call() }
                .onSuccess { decided ->
                    AppLog.i(logTag) { "booking ${decided.reference} is now ${decided.status}" }
                    setState { copy(requests = replace(decided), working = false, selectedId = nextToAnswer(decided.id)) }
                    announce(decided, guest)
                }
                .onFailure { failure ->
                    setState { copy(working = false) }
                    sendEffect(AdminRequestsEffect.ShowRefused(overlap = failure.isOverlap()))
                }
        }
    }

    private fun declineSelected() {
        val declining = state.value.declining ?: return
        val alternative = state.value.alternative.takeIf { it != AlternativeOffer.NONE }
        val reason = state.value.reason
        setState { copy(declining = null) }
        decide(declining.id) { desk.decline(declining.id, reason, alternative?.name) }
    }

    private fun announce(decided: DeskRequest, guest: String) = when (decided.status) {
        DeskStatus.CONFIRMED -> sendEffect(AdminRequestsEffect.ShowConfirmed(guest))

        DeskStatus.DECLINED -> sendEffect(
            AdminRequestsEffect.ShowDeclined(guest, withAlternative = state.value.alternative != AlternativeOffer.NONE),
        )

        DeskStatus.COMPLETED -> sendEffect(AdminRequestsEffect.ShowVisitRecorded(guest, attended = true))

        DeskStatus.NO_SHOW -> sendEffect(AdminRequestsEffect.ShowVisitRecorded(guest, attended = false))

        else -> Unit
    }

    private fun AdminRequestsState.replace(decided: DeskRequest) = requests.map { if (it.id == decided.id) decided else it }

    /** The queue is worked oldest-waiting first, so the desk never has to hunt for the next one. */
    private fun AdminRequestsState.nextToAnswer(decidedId: String): String? =
        requests.firstOrNull { it.status == DeskStatus.PENDING && it.id != decidedId }?.id ?: selectedId

    private fun Throwable.isOverlap(): Boolean = (this as? DeskRefused)?.code == BookingErrorCodes.OVERLAPS_ANOTHER

    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // Ktor's wasm engine reports a failed fetch as kotlin.Error, which is not an Exception.
        AppLog.w(logTag, e) { "desk call failed (${e::class.simpleName})" }
        Result.failure(e)
    }
}
