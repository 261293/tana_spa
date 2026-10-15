package com.tana.spa.shared.spa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PromoCodesTest {
    private val facial = SpaCatalog.service("jade-facial")!!
    private val massage = SpaCatalog.service("stone")!!
    private val cheapMassage = SpaCatalog.service("deep")!!

    @Test
    fun percentage_code_discounts_an_eligible_treatment() {
        val result = checkPromoCode(" jade20 ", facial)
        assertIs<PromoCheck.Applied>(result)
        assertEquals(24, result.discountUsd) // 20% of $120
    }

    @Test
    fun fixed_amount_code_takes_its_amount() {
        val result = checkPromoCode("MIDWEEK15", massage)
        assertIs<PromoCheck.Applied>(result)
        assertEquals(15, result.discountUsd)
    }

    @Test
    fun code_is_refused_for_the_wrong_category() {
        val result = checkPromoCode("JADE20", massage)
        assertIs<PromoCheck.Rejected>(result)
        assertEquals(PromoRejection.WRONG_CATEGORY, result.reason)
    }

    @Test
    fun code_is_refused_below_its_minimum_spend() {
        assertEquals(140, cheapMassage.priceUsd)
        val result = checkPromoCode("MIDWEEK15", cheapMassage.copy(priceUsd = 110))
        assertIs<PromoCheck.Rejected>(result)
        assertEquals(PromoRejection.BELOW_MINIMUM_SPEND, result.reason)
    }

    @Test
    fun unknown_and_empty_codes_are_told_apart() {
        assertEquals(PromoRejection.UNKNOWN_CODE, (checkPromoCode("NOPE", facial) as PromoCheck.Rejected).reason)
        assertEquals(PromoRejection.EMPTY, (checkPromoCode("  ", facial) as PromoCheck.Rejected).reason)
        assertEquals(PromoRejection.NO_SERVICE_CHOSEN, (checkPromoCode("JADE20", null) as PromoCheck.Rejected).reason)
    }
}
