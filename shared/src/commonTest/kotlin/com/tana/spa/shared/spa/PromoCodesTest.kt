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
        val result = checkPromoCode(" jade20 ", facial, SpaCatalog.today)
        assertIs<PromoCheck.Applied>(result)
        assertEquals(600_000, result.discountVnd)
    }

    @Test
    fun fixed_amount_code_takes_its_amount() {
        val result = checkPromoCode("MIDWEEK15", massage, SpaCatalog.today)
        assertIs<PromoCheck.Applied>(result)
        assertEquals(375_000, result.discountVnd)
    }

    @Test
    fun code_is_refused_for_the_wrong_category() {
        val result = checkPromoCode("JADE20", massage, SpaCatalog.today)
        assertIs<PromoCheck.Rejected>(result)
        assertEquals(PromoRejection.WRONG_CATEGORY, result.reason)
    }

    @Test
    fun code_is_refused_below_its_minimum_spend() {
        assertEquals(3_500_000, cheapMassage.priceVnd)
        val result = checkPromoCode("MIDWEEK15", cheapMassage.copy(priceVnd = 2_750_000), SpaCatalog.today)
        assertIs<PromoCheck.Rejected>(result)
        assertEquals(PromoRejection.BELOW_MINIMUM_SPEND, result.reason)
    }

    @Test
    fun unknown_and_empty_codes_are_told_apart() {
        assertEquals(PromoRejection.UNKNOWN_CODE, (checkPromoCode("NOPE", facial, SpaCatalog.today) as PromoCheck.Rejected).reason)
        assertEquals(PromoRejection.EMPTY, (checkPromoCode("  ", facial, SpaCatalog.today) as PromoCheck.Rejected).reason)
        assertEquals(PromoRejection.NO_SERVICE_CHOSEN, (checkPromoCode("JADE20", null, SpaCatalog.today) as PromoCheck.Rejected).reason)
    }

    @Test
    fun a_code_past_its_last_day_is_refused_as_expired() {
        val result = checkPromoCode("JADE20", facial, SpaDate(2026, 11, 1))
        assertIs<PromoCheck.Rejected>(result)
        assertEquals(PromoRejection.EXPIRED, result.reason)
        assertIs<PromoCheck.Applied>(checkPromoCode("JADE20", facial, SpaDate(2026, 10, 31)), "the last day still counts")
    }

    @Test
    fun only_open_offers_are_listed() {
        assertEquals(listOf("MIDWEEK15", "FIRSTVISIT"), SpaCatalog.livePromos(SpaDate(2026, 11, 12)).map { it.code })
    }
}
