package com.tana.spa.core.ui

import com.tana.spa.shared.spa.Promo
import com.tana.spa.shared.spa.ServiceCategory
import com.tana.spa.shared.spa.SpaCatalog
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SpaPromoTextTest {

    private fun promo(code: String): Promo = SpaCatalog.promos.first { it.code == code }

    @AfterTest
    fun restoreTheDefaultLanguage() {
        SelectedLanguage.value = AppLanguage.VIETNAMESE
    }

    @Test
    fun a_vietnamese_app_reads_the_offer_in_vietnamese() {
        SelectedLanguage.value = AppLanguage.VIETNAMESE
        val first = promo("FIRSTVISIT")

        assertEquals("Giảm 10% cho lần đặt đầu tiên", first.localizedTitle)
        assertEquals("Dành cho khách mới. Mọi liệu trình.", first.localizedTerms)
        assertEquals("31 thg 12, 2026", first.localizedValidUntil)
    }

    @Test
    fun an_english_app_reads_the_offer_as_the_spa_wrote_it() {
        SelectedLanguage.value = AppLanguage.ENGLISH
        val first = promo("FIRSTVISIT")

        assertEquals("10% off your first booking", first.localizedTitle)
        assertEquals("New guests. Any treatment.", first.localizedTerms)
        assertEquals("Dec 31, 2026", first.localizedValidUntil)
    }

    @Test
    fun the_money_in_an_offer_is_written_in_the_currency_that_language_shows() {
        val midweek = promo("MIDWEEK15")

        SelectedLanguage.value = AppLanguage.ENGLISH
        assertEquals("\$15 off weekday massages", midweek.localizedTitle)

        SelectedLanguage.value = AppLanguage.VIETNAMESE
        // The prose must agree with formatPrice, which shows the same offer converted.
        assertEquals("Giảm ${formatPrice(15)} cho massage ngày thường", midweek.localizedTitle)
    }

    @Test
    fun the_treatments_a_code_applies_to_are_named_in_the_language_asked_for() {
        val facialOnly = promo("JADE20")

        SelectedLanguage.value = AppLanguage.VIETNAMESE
        assertEquals("chăm sóc da mặt", facialOnly.localizedEligibleLabel())
        assertEquals("mọi liệu trình", promo("FIRSTVISIT").localizedEligibleLabel())

        SelectedLanguage.value = AppLanguage.ENGLISH
        assertEquals("facial", facialOnly.localizedEligibleLabel())
        assertEquals("all", promo("FIRSTVISIT").localizedEligibleLabel())
    }

    @Test
    fun an_offer_written_in_one_language_only_is_still_shown_in_the_other() {
        val untranslated = Promo(
            code = "GIFT50",
            title = "Gift card credit",
            validUntil = "Jan 1, 2030",
            terms = "Redeemable once.",
        )

        SelectedLanguage.value = AppLanguage.VIETNAMESE
        assertEquals("Gift card credit", untranslated.localizedTitle)
        assertEquals("Redeemable once.", untranslated.localizedTerms)
        assertEquals("Jan 1, 2030", untranslated.localizedValidUntil)
    }

    @Test
    fun every_sample_offer_is_written_in_both_languages() {
        SpaCatalog.promos.forEach { promo ->
            SelectedLanguage.value = AppLanguage.VIETNAMESE
            val vietnamese = promo.localizedTitle
            SelectedLanguage.value = AppLanguage.ENGLISH
            assertEquals(false, vietnamese == promo.localizedTitle, "${promo.code} has no Vietnamese title")
        }
    }

    @Test
    fun a_category_keeps_its_english_name_when_the_app_is_in_english() {
        SelectedLanguage.value = AppLanguage.ENGLISH
        ServiceCategory.entries.forEach { assertEquals(it.label, it.localizedLabel()) }
    }
}
