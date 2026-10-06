package com.tana.spa.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneNumbersTest {

    @Test
    fun everyWayOfTypingAVietnameseNumberGivesTheSameE164() {
        listOf("0333433969", " 0333 433 969 ", "0333.433.969", "84333433969", "+84333433969", "+84 (333) 433-969")
            .forEach { typed ->
                val parsed = PhoneNumbers.parse(typed)
                assertEquals("+84333433969", parsed?.e164, "input: $typed")
                assertEquals("VN", parsed?.region)
            }
    }

    @Test
    fun numbersFromAroundTheWorldAreRecognisedWithTheirRegion() {
        mapOf(
            "+1 650-253-0000" to ("+16502530000" to "US"),
            "+44 20 7031 3000" to ("+442070313000" to "GB"),
            "+61 2 9374 4000" to ("+61293744000" to "AU"),
            "+65 6521 8000" to ("+6565218000" to "SG"),
            "+81 3-6384-9000" to ("+81363849000" to "JP"),
        ).forEach { (typed, expected) ->
            val parsed = PhoneNumbers.parse(typed)
            assertEquals(expected.first, parsed?.e164, "input: $typed")
            assertEquals(expected.second, parsed?.region, "input: $typed")
        }
    }

    @Test
    fun defaultRegionDecidesHowALocalNumberIsRead() {
        // The same national digits mean different numbers in different countries.
        assertEquals("+16502530000", PhoneNumbers.parse("650-253-0000", defaultRegion = "US")?.e164)
        assertNull(PhoneNumbers.parse("650-253-0000", defaultRegion = "VN"))
    }

    @Test
    fun garbageAndUnusableNumbersAreRejected() {
        listOf(
            "", "   ", "abc", "12345", "0133433969", "033343396", "+999 123456",
            "0333433969 ext 12", // an extension cannot receive an SMS
            "+800 1234 5678", // valid but non-geographic (international freephone): not a person's phone
            "0".repeat(40),
        ).forEach { assertNull(PhoneNumbers.parse(it), "input: '$it'") }
    }

    @Test
    fun maskKeepsOnlyTheCountryCodeAndLastDigits() {
        assertEquals("+84••••••969", PhoneNumbers.mask("+84333433969"))
        assertEquals("+1•••••••000", PhoneNumbers.mask("+16502530000"))
        assertEquals("+123", PhoneNumbers.mask("+123")) // too short to mask meaningfully
    }
}
