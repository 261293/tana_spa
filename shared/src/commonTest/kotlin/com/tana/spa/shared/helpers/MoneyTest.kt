package com.tana.spa.shared.helpers

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyTest {

    @Test
    fun groups_thousands_with_dots_and_ends_with_the_dong_sign() {
        assertEquals("1.200.000đ", formatVnd(1_200_000))
        assertEquals("450.000đ", formatVnd(450_000))
    }

    @Test
    fun small_amounts_need_no_separator() {
        assertEquals("0đ", formatVnd(0))
        assertEquals("999đ", formatVnd(999))
        assertEquals("1.000đ", formatVnd(1_000))
    }

    @Test
    fun a_negative_amount_keeps_its_sign_in_front() {
        assertEquals("-12.500đ", formatVnd(-12_500))
    }
}
