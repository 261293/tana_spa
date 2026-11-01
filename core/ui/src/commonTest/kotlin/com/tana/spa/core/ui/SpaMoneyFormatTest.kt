package com.tana.spa.core.ui

import com.tana.spa.shared.helpers.VND_PER_USD
import com.tana.spa.shared.helpers.usdToVnd
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SpaMoneyFormatTest {

    @AfterTest
    fun restoreTheDefaultLanguage() {
        SelectedLanguage.value = AppLanguage.VIETNAMESE
    }

    @Test
    fun vietnamese_prices_in_dong_with_the_thousands_grouped() {
        SelectedLanguage.value = AppLanguage.VIETNAMESE
        assertEquals("2.500.000đ", formatPrice(100))
        assertEquals("4.125.000đ", formatPrice(165))
    }

    @Test
    fun english_prices_in_dollars() {
        SelectedLanguage.value = AppLanguage.ENGLISH
        assertEquals("\$100", formatPrice(100))
        assertEquals("\$165", formatPrice(165))
    }

    @Test
    fun a_free_treatment_reads_as_free_in_both_languages_rather_than_blank() {
        SelectedLanguage.value = AppLanguage.VIETNAMESE
        assertEquals("0đ", formatPrice(0))
        SelectedLanguage.value = AppLanguage.ENGLISH
        assertEquals("\$0", formatPrice(0))
    }

    @Test
    fun the_same_amount_is_shown_in_two_currencies_at_one_rate() {
        assertEquals(100 * VND_PER_USD, usdToVnd(100))
        assertEquals(0L, usdToVnd(0))
    }

    @Test
    fun switching_language_restates_the_price_without_the_caller_doing_anything() {
        SelectedLanguage.value = AppLanguage.ENGLISH
        val english = formatPrice(120)
        SelectedLanguage.value = AppLanguage.VIETNAMESE
        val vietnamese = formatPrice(120)
        assertEquals("\$120", english)
        assertEquals("3.000.000đ", vietnamese)
    }
}
