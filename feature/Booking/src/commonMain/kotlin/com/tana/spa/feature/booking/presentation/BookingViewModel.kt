package com.tana.spa.feature.booking.presentation

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.booking.domain.BookingAttempt
import com.tana.spa.feature.booking.domain.GuestBookingRepository
import com.tana.spa.feature.booking.domain.SpaCatalogRepository
import com.tana.spa.shared.spa.PromoCheck
import com.tana.spa.shared.spa.SpaCatalog
import com.tana.spa.shared.spa.SpaDate
import com.tana.spa.shared.spa.checkPromoCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Holds one booking in progress. The menu comes from [catalog] and the open times from
 * [bookings], which is also what the request is sent through: this screen never invents a slug
 * or a time of its own.
 */
class BookingViewModel(private val catalog: SpaCatalogRepository, private val bookings: GuestBookingRepository) :
    MviViewModel<BookingState, BookingEvent, BookingEffect>(BookingState()) {

    init {
        loadCatalog()
        loadSlots(state.value.day, therapistSlug = null)
    }

    override fun handleEvent(event: BookingEvent) = when (event) {
        is BookingEvent.CodeOffered -> setState { withOfferedCode(event.code) }

        is BookingEvent.ServiceSelected -> setState {
            val chosen = copy(serviceId = event.id)
            // The code was checked against the old treatment; eligibility may no longer hold.
            chosen.copy(check = check?.let { checkPromoCode(code, chosen.service) })
        }

        is BookingEvent.TherapistSelected -> {
            setState { copy(therapistId = event.id) }
            // Another pair of hands has another diary, so the open times change with them.
            loadSlots(state.value.day, state.value.therapistSlug())
        }

        is BookingEvent.DaySelected -> {
            setState { copy(day = event.day, slotAtEpochMillis = null) }
            loadSlots(event.day, state.value.therapistSlug())
        }

        is BookingEvent.SlotSelected -> setState { copy(slotAtEpochMillis = event.startAtEpochMillis) }

        is BookingEvent.CodeChanged -> setState { copy(code = event.value.uppercase(), check = null) }

        BookingEvent.ApplyCode -> setState {
            val result = checkPromoCode(code, service)
            AppLog.i(logTag) { "promo code ${if (result is PromoCheck.Applied) "applied" else "refused"}" }
            copy(check = result)
        }

        BookingEvent.RetryCatalog -> {
            loadCatalog()
            loadSlots(state.value.day, state.value.therapistSlug())
        }

        BookingEvent.Submit -> submit()

        BookingEvent.DismissSent -> setState { copy(sent = null) }

        BookingEvent.OpenNotifications -> {
            setState { copy(sent = null) }
            sendEffect(BookingEffect.NavigateToNotifications)
        }

        BookingEvent.OpenBookings -> {
            setState { copy(sent = null) }
            sendEffect(BookingEffect.NavigateToBookings)
        }
    }

    /** Null is "any therapist": the request carries no therapist and the spa assigns one. */
    private fun BookingState.therapistSlug(): String? = therapistId.takeIf { it != ANY_THERAPIST }

    private fun submit() {
        val current = state.value
        val service = current.service ?: return
        val slot = current.slot ?: return
        if (current.sending) return
        setState { copy(sending = true) }
        viewModelScope.launch {
            val attempt = bookings.create(
                serviceSlug = service.id,
                therapistSlug = current.therapistSlug(),
                startAtEpochMillis = slot.startAtEpochMillis,
                // Only a code the spa accepted is sent; an unchecked one would refuse the booking.
                code = current.code.takeIf { current.check is PromoCheck.Applied },
            )
            setState { copy(sending = false) }
            when (attempt) {
                is BookingAttempt.Done -> {
                    AppLog.i(logTag) { "booking ${attempt.booking.reference} requested (${attempt.booking.status})" }
                    setState { copy(sent = attempt.booking) }
                    // The slot the spa just took is no longer free for the next guest on this screen.
                    reloadSlots()
                }

                is BookingAttempt.Refused -> {
                    AppLog.i(logTag) { "booking refused: ${attempt.code}" }
                    sendEffect(BookingEffect.ShowRefusal(attempt.code))
                    reloadSlots()
                }

                BookingAttempt.Offline -> sendEffect(BookingEffect.ShowRefusal(null))
            }
        }
    }

    private fun reloadSlots() = loadSlots(state.value.day, state.value.therapistSlug())

    private fun loadSlots(day: SpaDate, therapistSlug: String?) {
        setState { copy(slotsLoading = true) }
        viewModelScope.launch {
            runCatchingCancellable { bookings.availability(day.epochDay, therapistSlug) }
                .onSuccess { slots ->
                    setState {
                        copy(
                            slots = slots,
                            slotsLoading = false,
                            // Keep the chosen time only while it is still on offer and still free.
                            slotAtEpochMillis = slotAtEpochMillis
                                ?.takeIf { chosen -> slots.any { it.startAtEpochMillis == chosen && it.available } },
                        )
                    }
                }
                .onFailure { setState { copy(slots = emptyList(), slotsLoading = false, slotAtEpochMillis = null) } }
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
        AppLog.w(logTag, e) { "booking call failed (${e::class.simpleName})" }
        Result.failure(e)
    }
}
