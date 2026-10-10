package com.tana.spa.shared.featureflags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureFlagsTest {

    @Test
    fun defaultsAreTheStatesCompiledIntoTheCatalog() {
        for (flag in FeatureFlag.entries) {
            assertEquals(flag.defaultEnabled, FeatureFlags.defaults.isEnabled(flag), flag.key)
        }
    }

    @Test
    fun phoneOtpVerificationShipsSwitchedOff() {
        assertFalse(FeatureFlags.defaults.isEnabled(FeatureFlag.PHONE_OTP_VERIFICATION))
    }

    @Test
    fun anOverrideWinsOverTheDefaultAndOtherFlagsKeepTheirs() {
        val flags = ConfigurableFeatureFlags(mapOf(FeatureFlag.PHONE_OTP_VERIFICATION to true))

        assertTrue(flags.isEnabled(FeatureFlag.PHONE_OTP_VERIFICATION))
        for (flag in FeatureFlag.entries - FeatureFlag.PHONE_OTP_VERIFICATION) {
            assertEquals(flag.defaultEnabled, flags.isEnabled(flag), flag.key)
        }
    }

    @Test
    fun everyTruthyAndFalsySpellingIsUnderstood() {
        val name = FeatureFlag.PHONE_OTP_VERIFICATION.envName
        for (value in listOf("true", "TRUE", " 1 ", "yes", "on", "enabled")) {
            val flags = ConfigurableFeatureFlags.fromEnvironment(mapOf(name to value))
            assertTrue(flags.isEnabled(FeatureFlag.PHONE_OTP_VERIFICATION), value)
        }
        for (value in listOf("false", "False", "0", "no", "off", "disabled")) {
            val flags = ConfigurableFeatureFlags.fromEnvironment(mapOf(name to value))
            assertFalse(flags.isEnabled(FeatureFlag.PHONE_OTP_VERIFICATION), value)
        }
    }

    @Test
    fun environmentVariablesAreNamedAfterTheFlagKey() {
        assertEquals("FEATURE_PHONE_OTP_VERIFICATION", FeatureFlag.PHONE_OTP_VERIFICATION.envName)
        assertEquals(FeatureFlag.PHONE_OTP_VERIFICATION, FeatureFlag.fromEnvName("feature_phone_otp_verification"))
        assertEquals(null, FeatureFlag.fromEnvName("FEATURE_SOMETHING_ELSE"))
    }

    @Test
    fun anEmptyEnvironmentChangesNothing() {
        val flags = ConfigurableFeatureFlags.fromEnvironment(emptyMap())
        for (flag in FeatureFlag.entries) {
            assertEquals(flag.defaultEnabled, flags.isEnabled(flag), flag.key)
        }
    }

    @Test
    fun aVariableThatIsNotOursIsIgnoredRatherThanFatal() {
        // The FEATURE_ prefix is generic, so another tool's variable must not stop boot.
        val flags = ConfigurableFeatureFlags.fromEnvironment(mapOf("FEATURE_FLAGS_URL" to "https://example.com"))
        assertFalse(flags.isEnabled(FeatureFlag.PHONE_OTP_VERIFICATION))
    }

    @Test
    fun aValueThatIsNotABooleanIsRejectedAtStartupNotSilentlyIgnored() {
        val error = assertFailsWith<IllegalArgumentException> {
            ConfigurableFeatureFlags.fromEnvironment(mapOf(FeatureFlag.PHONE_OTP_VERIFICATION.envName to "ture"))
        }
        assertTrue(error.message!!.contains("FEATURE_PHONE_OTP_VERIFICATION"), error.message!!)
    }

    @Test
    fun describeListsEveryFlagForTheStartupLog() {
        val flags = ConfigurableFeatureFlags(mapOf(FeatureFlag.PHONE_OTP_VERIFICATION to true))
        assertTrue(flags.describe().contains("phone_otp_verification=on"), flags.describe())
        assertTrue(FeatureFlags.defaults.describe().contains("phone_otp_verification=off"))
    }
}
