package com.tana.spa.shared.featureflags

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun describeListsEveryFlagForTheStartupLog() {
        val flags = ConfigurableFeatureFlags(mapOf(FeatureFlag.PHONE_OTP_VERIFICATION to true))
        assertTrue(flags.describe().contains("phone_otp_verification=on"), flags.describe())
        assertTrue(FeatureFlags.defaults.describe().contains("phone_otp_verification=off"))
    }
}
