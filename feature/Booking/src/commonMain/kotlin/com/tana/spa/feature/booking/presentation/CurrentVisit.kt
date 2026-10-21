package com.tana.spa.feature.booking.presentation

import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.feature.booking.domain.GuestBooking
import com.tana.spa.feature.booking.domain.GuestBookingRepository
import com.tana.spa.feature.booking.domain.currentVisit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/** What the strip draws: the visit, and whether it was running when the app last looked. */
data class VisitSnapshot(val booking: GuestBooking, val underWay: Boolean)

/**
 * The visit the app frame keeps in front of the guest, read by the strip above the destinations.
 *
 * A plain holder rather than a ViewModel, like the unread badge it sits beside: the frame outlives
 * every screen in it, so this must not be owned by whichever one happens to be on top.
 */
class CurrentVisit(private val bookings: GuestBookingRepository, private val sessions: AccessTokenSource) {
    private val state = MutableStateFlow<VisitSnapshot?>(null)

    val visit: StateFlow<VisitSnapshot?> = state.asStateFlow()

    /** Silent on failure: the strip is a convenience, and a visit nobody can read is simply not shown. */
    suspend fun refresh() {
        if (sessions.accessToken() == null) {
            state.value = null
            return
        }
        try {
            // The clock is read here rather than while the strip draws, so what it says is decided
            // once per refresh instead of depending on when a recomposition happens to land.
            val now = Clock.System.now().toEpochMilliseconds()
            state.value = bookings.mine().currentVisit(now)?.let { VisitSnapshot(it, it.inProgress(now)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Ktor's wasm engine reports a failed fetch as kotlin.Error, which is not an Exception.
            AppLog.w(TAG, e) { "current visit unavailable (${e::class.simpleName})" }
        }
    }

    private companion object {
        const val TAG = "CurrentVisit"
    }
}
