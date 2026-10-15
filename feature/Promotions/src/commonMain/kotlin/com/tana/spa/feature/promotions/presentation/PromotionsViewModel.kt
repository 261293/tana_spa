package com.tana.spa.feature.promotions.presentation

import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.shared.spa.SpaCatalog

class PromotionsViewModel : MviViewModel<PromotionsState, PromotionsEvent, PromotionsEffect>(PromotionsState()) {

    override fun handleEvent(event: PromotionsEvent) = when (event) {
        is PromotionsEvent.EntryChanged -> setState {
            copy(entry = event.value.uppercase(), checked = null, checkFailed = false)
        }

        PromotionsEvent.CheckEntry -> setState {
            // Checked against the catalogue only: eligibility needs a treatment, which the
            // booking screen asks for.
            val match = SpaCatalog.promos.firstOrNull { it.code == entry.trim() }
            copy(checked = match, checkFailed = match == null)
        }

        is PromotionsEvent.CodeCopied -> sendEffect(PromotionsEffect.ShowCopied(event.code))

        is PromotionsEvent.UseCode -> sendEffect(PromotionsEffect.OpenBooking(event.code))
    }
}
