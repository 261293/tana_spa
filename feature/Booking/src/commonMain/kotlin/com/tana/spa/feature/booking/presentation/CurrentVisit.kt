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

data class VisitSnapshot(val booking: GuestBooking, val underWay: Boolean)

class CurrentVisit(private val bookings: GuestBookingRepository, private val sessions: AccessTokenSource) {
    private val state = MutableStateFlow<VisitSnapshot?>(null)

    val visit: StateFlow<VisitSnapshot?> = state.asStateFlow()

    suspend fun refresh() {
        if (sessions.accessToken() == null) {
            state.value = null
            return
        }
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            state.value = bookings.mine().currentVisit(now)?.let { VisitSnapshot(it, it.inProgress(now)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLog.w(TAG, e) { "current visit unavailable (${e::class.simpleName})" }
        }
    }

    private companion object {
        const val TAG = "CurrentVisit"
    }
}
