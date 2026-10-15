package com.tana.spa.feature.booking.presentation

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.booking.domain.SpaCatalogRepository
import com.tana.spa.shared.spa.PromoCheck
import com.tana.spa.shared.spa.SpaCatalog
import com.tana.spa.shared.spa.checkPromoCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Holds one booking in progress. The treatments and therapists come from [catalog]; there is no
 * booking endpoint in the app yet, so a request only moves the screen into its confirmation state.
 */
class BookingViewModel(private val catalog: SpaCatalogRepository) :
    MviViewModel<BookingState, BookingEvent, BookingEffect>(BookingState()) {

    init {
        loadCatalog()
    }

    override fun handleEvent(event: BookingEvent) = when (event) {
        is BookingEvent.CodeOffered -> setState { withOfferedCode(event.code) }

        is BookingEvent.ServiceSelected -> setState {
            val chosen = copy(serviceId = event.id)
            // The code was checked against the old treatment; eligibility may no longer hold.
            chosen.copy(check = check?.let { checkPromoCode(code, chosen.service) })
        }

        is BookingEvent.TherapistSelected -> setState { copy(therapistId = event.id) }

        // A different day cannot keep the time that was free on the old one.
        is BookingEvent.DaySelected -> setState { copy(day = event.day, slot = null) }

        is BookingEvent.SlotSelected -> setState { copy(slot = event.slot) }

        is BookingEvent.CodeChanged -> setState { copy(code = event.value.uppercase(), check = null) }

        BookingEvent.ApplyCode -> setState {
            val result = checkPromoCode(code, service)
            AppLog.i(logTag) { "promo code ${if (result is PromoCheck.Applied) "applied" else "refused"}" }
            copy(check = result)
        }

        BookingEvent.RetryCatalog -> loadCatalog()

        BookingEvent.Submit -> {
            val current = state.value
            AppLog.i(logTag) { "booking requested: ${current.serviceId} with ${current.therapistId}" }
            setState { copy(requestSent = true) }
        }

        BookingEvent.DismissSent -> setState { copy(requestSent = false) }

        BookingEvent.OpenNotifications -> {
            setState { copy(requestSent = false) }
            sendEffect(BookingEffect.NavigateToNotifications)
        }
    }

    private fun loadCatalog() {
        setState { copy(catalogLoading = true, catalogFailed = false) }
        viewModelScope.launch {
            runCatchingCancellable { catalog.catalog() }
                .onSuccess { loaded ->
                    AppLog.i(logTag) { "catalogue: ${loaded.services.size} treatments, ${loaded.therapists.size} therapists" }
                    setState {
                        val menu = copy(
                            services = loaded.services,
                            therapists = loaded.therapists,
                            serviceId = serviceId ?: loaded.services.firstOrNull()?.id,
                            therapistId = therapistId ?: loaded.therapists.firstOrNull()?.id,
                            catalogLoading = false,
                            catalogFailed = false,
                        )
                        // An offered code can arrive from the offers screen before the menu does.
                        if (menu.code.isNotEmpty() && menu.check == null) menu.withOfferedCode(menu.code) else menu
                    }
                }
                .onFailure { setState { copy(catalogLoading = false, catalogFailed = true) } }
        }
    }

    /** Picks a treatment [raw] is valid for: a facial-only code on a massage would read as a refusal. */
    private fun BookingState.withOfferedCode(raw: String): BookingState {
        val code = raw.uppercase()
        val eligible = SpaCatalog.promos.firstOrNull { it.code == code }?.eligible.orEmpty()
        val treatment = service?.takeIf { eligible.isEmpty() || it.category in eligible }
            ?: services.firstOrNull { eligible.isEmpty() || it.category in eligible }
            ?: service
        return copy(
            serviceId = treatment?.id ?: serviceId,
            code = code,
            check = treatment?.let { checkPromoCode(code, it) },
        )
    }

    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // Ktor's wasm engine reports a failed fetch as kotlin.Error, which is not an Exception.
        AppLog.w(logTag, e) { "catalogue call failed (${e::class.simpleName})" }
        Result.failure(e)
    }
}
