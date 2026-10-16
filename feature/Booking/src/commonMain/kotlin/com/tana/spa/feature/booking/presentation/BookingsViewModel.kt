package com.tana.spa.feature.booking.presentation

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.booking.domain.BookingAttempt
import com.tana.spa.feature.booking.domain.GuestBookingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** The guest's own visits, as the spa has them: status, the reason for a refusal, and cancelling. */
class BookingsViewModel(private val bookings: GuestBookingRepository) :
    MviViewModel<BookingsState, BookingsEvent, BookingsEffect>(BookingsState()) {

    init {
        refresh()
    }

    override fun handleEvent(event: BookingsEvent) {
        when (event) {
            BookingsEvent.Refresh -> refresh()

            is BookingsEvent.CancelStarted -> setState {
                copy(cancelling = bookings.firstOrNull { it.id == event.id })
            }

            BookingsEvent.CancelDismissed -> setState { copy(cancelling = null) }

            BookingsEvent.CancelConfirmed -> cancel()
        }
    }

    private fun refresh() {
        setState { copy(loading = true, failed = false) }
        viewModelScope.launch {
            runCatchingCancellable { bookings.mine() }
                .onSuccess { mine ->
                    AppLog.i(logTag) { "loaded ${mine.size} bookings" }
                    setState { copy(bookings = mine, loading = false, failed = false, nowEpochMillis = now()) }
                }
                .onFailure { setState { copy(loading = false, failed = true) } }
        }
    }

    private fun cancel() {
        val booking = state.value.cancelling ?: return
        setState { copy(cancelling = null, working = true) }
        viewModelScope.launch {
            when (val attempt = bookings.cancel(booking.id)) {
                is BookingAttempt.Done -> {
                    AppLog.i(logTag) { "booking ${booking.reference} cancelled by its guest" }
                    setState {
                        copy(
                            bookings = this.bookings.map { if (it.id == attempt.booking.id) attempt.booking else it },
                            working = false,
                            nowEpochMillis = now(),
                        )
                    }
                    sendEffect(BookingsEffect.ShowCancelled)
                }

                is BookingAttempt.Refused -> {
                    setState { copy(working = false) }
                    sendEffect(BookingsEffect.ShowRefusal(attempt.code))
                    // Whatever the spa refused it for, the list on screen is out of date.
                    refresh()
                }

                BookingAttempt.Offline -> {
                    setState { copy(working = false) }
                    sendEffect(BookingsEffect.ShowRefusal(null))
                }
            }
        }
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // Ktor's wasm engine reports a failed fetch as kotlin.Error, which is not an Exception.
        AppLog.w(logTag, e) { "bookings call failed (${e::class.simpleName})" }
        Result.failure(e)
    }
}
