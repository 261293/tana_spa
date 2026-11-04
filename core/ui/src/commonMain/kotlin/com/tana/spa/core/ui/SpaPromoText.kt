package com.tana.spa.core.ui

import com.tana.spa.shared.spa.Promo
import com.tana.spa.shared.spa.ServiceCategory

/**
 * An offer's wording in the language the app is drawing itself in. The offers are data rather than
 * resources, so they carry both languages and are picked here, the same way [formatPrice] picks the
 * currency. A code written in one language only is shown to everybody rather than coming back blank.
 */
val Promo.localizedTitle: String get() = pick(titleVi, title)

val Promo.localizedTerms: String get() = pick(termsVi, terms)

val Promo.localizedValidUntil: String get() = pick(validUntilVi, validUntil)

/** The categories a code applies to, spelled for a sentence ("chăm sóc da mặt, toàn thân"). */
fun Promo.localizedEligibleLabel(): String = if (eligible.isEmpty()) {
    if (vietnamese) "mọi liệu trình" else "all"
} else {
    eligible.joinToString(", ") { it.localizedLabel().lowercase() }
}

fun ServiceCategory.localizedLabel(): String = if (vietnamese) {
    when (this) {
        ServiceCategory.MASSAGE -> "Massage"
        ServiceCategory.FACIAL -> "Chăm sóc da mặt"
        ServiceCategory.BODY -> "Toàn thân"
    }
} else {
    label
}

private fun pick(translated: String?, base: String): String = translated?.takeIf { vietnamese && it.isNotBlank() } ?: base
