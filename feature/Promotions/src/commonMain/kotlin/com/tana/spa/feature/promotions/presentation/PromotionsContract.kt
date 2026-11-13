package com.tana.spa.feature.promotions.presentation

import com.tana.spa.shared.spa.Promo

/** Only offers still open [today]; [checked] is null until the guest checks a typed code. */
data class PromotionsState(val live: List<Promo>, val entry: String = "", val checked: Promo? = null, val checkFailed: Boolean = false) {
    val featured: Promo? get() = live.firstOrNull()
    val others: List<Promo> get() = live.drop(1)
}

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
