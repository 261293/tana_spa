package com.tana.spa.feature.booking.presentation

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.core.network.CallerRole
import com.tana.spa.feature.booking.domain.GuestBooking
import com.tana.spa.feature.booking.domain.GuestBookingRepository
import com.tana.spa.feature.booking.domain.Product
import com.tana.spa.feature.booking.domain.SpaCatalogRepository
import com.tana.spa.shared.auth.SpaRoles
import com.tana.spa.shared.booking.BookingStatusDto
import com.tana.spa.shared.spa.SpaCatalog
import com.tana.spa.shared.spa.SpaDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** Everything the spa offers on one page: its treatments, its shelf and how many offers are running. */
class HomeViewModel(
    private val catalog: SpaCatalogRepository,
    private val callerRole: CallerRole,
    private val bookings: GuestBookingRepository,
    private val today: SpaDate,
    private val now: () -> Long,
) : MviViewModel<HomeState, HomeEvent, HomeEffect>(HomeState()) {

    init {
        setState { copy(canBook = callerRole.role() != SpaRoles.STAFF, liveOffers = SpaCatalog.livePromos(today).size) }
        load()
    }

    override fun handleEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.Retry -> load()
            HomeEvent.Shown -> if (!state.value.loading) refreshNextVisit()
        }
    }

    private fun load() {
        setState { copy(loading = true, failed = false) }
        viewModelScope.launch {
            val shelf = async { shelf() }
            val next = async { nextVisit() }
            try {
                val services = catalog.catalog().services
                val products = shelf.await()
                val upcoming = next.await()
                setState { copy(loading = false, services = services, products = products, nextVisit = upcoming) }
                AppLog.i(logTag) { "home ${services.size} treatments, ${products.size} products" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLog.w(logTag, e) { "home catalogue failed (${e::class.simpleName})" }
                setState { copy(loading = false, failed = true) }
            }
        }
    }

    private fun refreshNextVisit() {
        viewModelScope.launch {
            val upcoming = nextVisit()
            setState { copy(nextVisit = upcoming) }
        }
    }

    private suspend fun nextVisit(): GuestBooking? {
        if (callerRole.role() == SpaRoles.STAFF) return null
        return try {
            val from = now()
            bookings.mine()
                .filter { it.status in UPCOMING && it.startAtEpochMillis > from }
                .minByOrNull { it.startAtEpochMillis }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLog.w(logTag, e) { "next visit failed (${e::class.simpleName})" }
            null
        }
    }

    /** The shelf is secondary to the treatments: without it the page still shows what can be booked. */
    private suspend fun shelf(): List<Product> = try {
        catalog.products()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppLog.w(logTag, e) { "shelf failed (${e::class.simpleName})" }
        emptyList()
    }

    private companion object {
        val UPCOMING = setOf(BookingStatusDto.PENDING, BookingStatusDto.CONFIRMED)
    }
}
