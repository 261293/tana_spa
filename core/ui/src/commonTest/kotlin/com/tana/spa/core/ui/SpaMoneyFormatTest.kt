package com.tana.spa.core.ui

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SpaMoneyFormatTest {

    @AfterTest
    fun restoreTheDefaultLanguage() {
        SelectedLanguage.value = AppLanguage.VIETNAMESE
    }

    @Test
    fun vietnamese_shows_dong_with_the_thousands_grouped() {
        SelectedLanguage.value = AppLanguage.VIETNAMESE
        assertEquals("4.125.000đ", formatPrice(4_125_000))
        assertEquals("3.000.000đ", formatPrice(3_000_000))
    }

    @Test
    fun english_shows_dong_too() {
        SelectedLanguage.value = AppLanguage.ENGLISH
        assertEquals("4.125.000đ", formatPrice(4_125_000))
        assertEquals("2.450.000đ", formatPrice(2_450_000))
    }

    @Test
    fun a_free_treatment_reads_as_zero_dong_in_both_languages() {
        SelectedLanguage.value = AppLanguage.VIETNAMESE
        assertEquals("0đ", formatPrice(0))
        SelectedLanguage.value = AppLanguage.ENGLISH
        assertEquals("0đ", formatPrice(0))
    }

    @Test
    fun switching_language_does_not_change_the_price() {
        SelectedLanguage.value = AppLanguage.ENGLISH
        val english = formatPrice(3_000_000)
        SelectedLanguage.value = AppLanguage.VIETNAMESE
        assertEquals(english, formatPrice(3_000_000))
    }
}
