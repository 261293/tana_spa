package com.tana.spa.feature.booking.presentation

import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.shared.spa.PromoCheck
import com.tana.spa.shared.spa.SpaCatalog
import com.tana.spa.shared.spa.checkPromoCode

/**
 * Holds one booking in progress. There is no booking endpoint yet, so a request only moves the
 * screen into its confirmation state; the catalogue it reads is [SpaCatalog].
 */
class BookingViewModel :
    MviViewModel<BookingState, BookingEvent, BookingEffect>(
        BookingState(serviceId = SpaCatalog.services.first().id, therapistId = SpaCatalog.therapists.first().id),
    ) {

    override fun handleEvent(event: BookingEvent) = when (event) {
        is BookingEvent.CodeOffered -> setState {
            val promo = SpaCatalog.promos.firstOrNull { it.code == event.code.uppercase() }
            val eligible = promo?.eligible.orEmpty()
            // Arriving with a facial-only code and a massage selected would read as a refusal.
            val treatment = service
                ?.takeIf { eligible.isEmpty() || it.category in eligible }
                ?: SpaCatalog.services.firstOrNull { eligible.isEmpty() || it.category in eligible }
                ?: service
            copy(
                serviceId = treatment?.id,
                code = event.code.uppercase(),
                check = checkPromoCode(event.code, treatment),
            )
        }

        is BookingEvent.ServiceSelected -> setState {
            // The code was checked against the old treatment; eligibility may no longer hold.
            copy(serviceId = event.id, check = check?.let { checkPromoCode(code, SpaCatalog.service(event.id)) })
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
}
