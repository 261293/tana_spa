package com.tana.spa.shared.spa

/** The outcome of typing a discount code against a chosen treatment. */
sealed interface PromoCheck {
    data class Applied(val promo: Promo, val discountUsd: Int) : PromoCheck

    /** [reason] says which rule the code failed, so the screen can word it. */
    data class Rejected(val reason: PromoRejection, val code: String, val promo: Promo? = null) : PromoCheck
}

enum class PromoRejection {
    EMPTY,
    UNKNOWN_CODE,
    NO_SERVICE_CHOSEN,
    WRONG_CATEGORY,
    BELOW_MINIMUM_SPEND,
}

/**
 * Checks [raw] against the live offers. A code is only ever accepted together with the treatment
 * it is being spent on, because eligibility and the minimum spend are properties of that pairing.
 */
fun checkPromoCode(raw: String, service: Service?): PromoCheck {
    val code = raw.trim().uppercase()
    if (code.isEmpty()) return PromoCheck.Rejected(PromoRejection.EMPTY, code)
    val promo = SpaCatalog.promos.firstOrNull { it.code == code }
        ?: return PromoCheck.Rejected(PromoRejection.UNKNOWN_CODE, code)
    if (service == null) return PromoCheck.Rejected(PromoRejection.NO_SERVICE_CHOSEN, code, promo)
    if (promo.eligible.isNotEmpty() && service.category !in promo.eligible) {
        return PromoCheck.Rejected(PromoRejection.WRONG_CATEGORY, code, promo)
    }
    if (promo.minSpendUsd != null && service.priceUsd < promo.minSpendUsd) {
        return PromoCheck.Rejected(PromoRejection.BELOW_MINIMUM_SPEND, code, promo)
    }
    val discount = promo.percentOff?.let { (service.priceUsd * it + 50) / 100 } ?: promo.amountOffUsd ?: 0
    return PromoCheck.Applied(promo, discount)
}

/** The categories a code applies to, spelled for a sentence ("facial, body"). */
fun Promo.eligibleLabel(): String = if (eligible.isEmpty()) "all" else eligible.joinToString(", ") { it.label.lowercase() }
