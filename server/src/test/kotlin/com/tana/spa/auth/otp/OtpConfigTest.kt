package com.tana.spa.auth.otp

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OtpConfigTest {

    @Test
    fun emptyAllowListMeansEveryCountry() {
        val config = OtpConfig()
        assertTrue(listOf("VN", "US", "GB", "BR", "NG").all(config::allows))
    }

    @Test
    fun allowListRestrictsAndBlockListWinsOverIt() {
        val config = OtpConfig(allowedCountries = setOf("VN", "US"), blockedCountries = setOf("US"))
        assertTrue(config.allows("VN"))
        assertFalse(config.allows("US")) // blocked beats allowed
        assertFalse(config.allows("GB")) // not on the allow list
    }

    @Test
    fun environmentIsParsedAndNormalised() {
        val config = OtpConfig.fromEnvironment(
            mapOf("OTP_ALLOWED_COUNTRIES" to " vn, us ,", "OTP_BLOCKED_COUNTRIES" to "kp", "DEFAULT_PHONE_REGION" to "sg"),
        )
        assertEquals(setOf("VN", "US"), config.allowedCountries)
        assertEquals(setOf("KP"), config.blockedCountries)
        assertEquals("SG", config.defaultPhoneRegion)
    }

    @Test
    fun anEmptyEnvironmentGivesTheSafeDefaults() {
        val config = OtpConfig.fromEnvironment(emptyMap())
        assertEquals(emptySet(), config.allowedCountries)
        assertEquals("VN", config.defaultPhoneRegion)
        assertEquals(Duration.ofMinutes(10), config.codeTtl)
        assertEquals(5, config.maxCheckAttempts)
    }

    @Test
    fun badCountryCodesAreRejectedAtStartupNotSilentlyIgnored() {
        assertFailsWith<IllegalArgumentException> { OtpConfig.fromEnvironment(mapOf("OTP_ALLOWED_COUNTRIES" to "VNM")) }
        assertFailsWith<IllegalArgumentException> { OtpConfig.fromEnvironment(mapOf("OTP_BLOCKED_COUNTRIES" to "V1")) }
    }

    @Test
    fun unsafeLimitsAreRejected() {
        assertFailsWith<IllegalArgumentException> { OtpConfig(maxCheckAttempts = 0) }
        assertFailsWith<IllegalArgumentException> { OtpConfig(maxCheckAttempts = 50) }
        assertFailsWith<IllegalArgumentException> { OtpConfig(codeTtl = Duration.ofDays(1)) }
    }

    @Test
    fun theDevProviderIsRefusedInProduction() {
        assertFailsWith<IllegalStateException> { PhoneVerificationProvider.fromEnvironment(mapOf("APP_ENV" to "production")) }
        assertEquals("dev", PhoneVerificationProvider.fromEnvironment(emptyMap()).name)
    }

    @Test
    fun twilioIsSelectedWhenFullyConfigured() {
        val provider = PhoneVerificationProvider.fromEnvironment(
            mapOf(
                "TWILIO_VERIFY_SERVICE_SID" to "VA1",
                "TWILIO_API_KEY_SID" to "SK1",
                "TWILIO_API_KEY_SECRET" to "x",
                "APP_ENV" to "production",
            ),
        )
        assertEquals("twilio_verify", provider.name)
    }

    @Test
    fun aHalfConfiguredTwilioDoesNotSilentlyFallBackToDevInProduction() {
        assertFailsWith<IllegalStateException> {
            PhoneVerificationProvider.fromEnvironment(
                mapOf("TWILIO_VERIFY_SERVICE_SID" to "VA1", "APP_ENV" to "production"),
            )
        }
    }
}
