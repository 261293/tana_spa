package com.tana.spa.feature.promotions.presentation

import com.tana.spa.shared.spa.Promo
import com.tana.spa.shared.spa.SpaCatalog

/** [checked] is null until the guest checks a typed code. */
data class PromotionsState(
    val featured: Promo = SpaCatalog.promos.first(),
    val others: List<Promo> = SpaCatalog.promos.drop(1),
    val entry: String = "",
    val checked: Promo? = null,
    val checkFailed: Boolean = false,
)

sealed interface PromotionsEvent {
    data class EntryChanged(val value: String) : PromotionsEvent
    data object CheckEntry : PromotionsEvent
    data class CodeCopied(val code: String) : PromotionsEvent
    data class UseCode(val code: String) : PromotionsEvent
}

sealed interface PromotionsEffect {
    data class ShowCopied(val code: String) : PromotionsEffect
    data class OpenBooking(val code: String) : PromotionsEffect
}
