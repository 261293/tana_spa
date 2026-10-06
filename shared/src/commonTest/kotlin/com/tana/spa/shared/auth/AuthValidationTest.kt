package com.tana.spa.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthValidationTest {
    @Test
    fun emailRules() {
        assertEquals(EmailError.EMPTY, AuthValidation.validateEmail("   "))
        assertEquals(EmailError.INVALID, AuthValidation.validateEmail("not-an-email"))
        assertEquals(EmailError.INVALID, AuthValidation.validateEmail("a@b"))
        assertNull(AuthValidation.validateEmail("  user.name+tag@example.co  "))
    }

    @Test
    fun passwordRules() {
        assertEquals(PasswordError.EMPTY, AuthValidation.validatePassword(""))
        assertEquals(PasswordError.TOO_SHORT, AuthValidation.validatePassword("1234567"))
        assertNull(AuthValidation.validatePassword("12345678"))
        assertNull(AuthValidation.validatePassword("  spaces ok  "))
    }

    @Test
    fun nameRules() {
        assertEquals(NameError.EMPTY, AuthValidation.validateName("   "))
        assertEquals(NameError.TOO_LONG, AuthValidation.validateName("a".repeat(101)))
        assertNull(AuthValidation.validateName("  Nguyễn Lê Phương Anh "))
        assertNull(AuthValidation.validateName("a".repeat(100)))
    }

    @Test
    fun emailIsNormalised() {
        assertEquals("a@b.com", AuthValidation.normalizeEmail("  A@B.Com "))
    }

    @Test
    fun everyWayOfWritingTheSameVietnameseNumberNormalisesIdentically() {
        val expected = "+84333433969"
        listOf(
            "0333433969",
            " 0333 433 969 ",
            "0333.433.969",
            "0333-433-969",
            "84333433969",
            "+84333433969",
            "+84 333 433 969",
            "+84 (333) 433-969",
        ).forEach { assertEquals(expected, AuthValidation.normalizePhone(it), "input: $it") }
    }

    @Test
    fun internationalNumbersNeedAPlus() {
        assertEquals("+14155552671", AuthValidation.normalizePhone("+1 415 555 2671"))
        assertNull(AuthValidation.normalizePhone("14155552671"))
    }

    @Test
    fun invalidPhonesAreRejected() {
        listOf(
            "", "   ", "abc", "033343396", "03334339691", "0233433969", "0133433969",
            "+84133433969", "+8433343396", "+1234567", "+1234567890123456", "0333-4x3-969",
        ).forEach { assertNull(AuthValidation.normalizePhone(it), "input: '$it'") }
    }

    @Test
    fun phoneValidationReportsEmptyVersusInvalid() {
        assertEquals(PhoneError.EMPTY, AuthValidation.validatePhone("  "))
        assertEquals(PhoneError.INVALID, AuthValidation.validatePhone("12345"))
        assertNull(AuthValidation.validatePhone("0333 433 969"))
    }
}
