package com.tana.spa.feature.appupdate.domain

import com.tana.spa.shared.appupdate.AppUpdateConfigResponse
import com.tana.spa.shared.appupdate.AppVersion
import kotlin.test.Test
import kotlin.test.assertEquals

private const val STORE = "https://example.test/app"

private fun config(minimum: String, latest: String, force: Boolean = false) =
    AppUpdateConfigResponse(minimumVersion = minimum, latestVersion = latest, forceUpdate = force)

private fun verdictOf(
    installed: String? = "1.5.0",
    config: AppUpdateConfigResponse? = config("1.0.0", "2.0.0"),
    gateEnabled: Boolean = true,
    storeUrl: String = STORE,
) = updateVerdict(installed, config, gateEnabled, storeUrl)

class UpdateVerdictTest {

    @Test
    fun belowTheFloorIsForced() {
        assertEquals(UpdateVerdict.Forced(AppVersion(2, 0, 0)), verdictOf(installed = "0.9.0"))
    }

    @Test
    fun exactlyAtTheFloorIsNotForcedBecauseEqualityIsNotBelow() {
        assertEquals(UpdateVerdict.Optional(AppVersion(2, 0, 0)), verdictOf(installed = "1.0.0"))
    }

    @Test
    fun aNewerReleaseWithoutForceIsOptional() {
        assertEquals(UpdateVerdict.Optional(AppVersion(2, 0, 0)), verdictOf(installed = "1.5.0"))
    }

    @Test
    fun forceUpdateEscalatesAnOptionalPromptToMandatory() {
        val escalated = verdictOf(installed = "1.5.0", config = config("1.0.0", "2.0.0", force = true))
        assertEquals(UpdateVerdict.Forced(AppVersion(2, 0, 0)), escalated)
    }

    @Test
    fun alreadyOnTheLatestVersionIsNeverPrompted() {
        assertEquals(UpdateVerdict.None, verdictOf(installed = "2.0.0"))
        assertEquals(UpdateVerdict.None, verdictOf(installed = "2.1.0"))
    }

    @Test
    fun forceUpdateDoesNotPromptSomeoneWhoHasNowhereToGo() {
        assertEquals(UpdateVerdict.None, verdictOf(installed = "2.0.0", config = config("1.0.0", "2.0.0", force = true)))
    }

    @Test
    fun theKillSwitchSilencesEverything() {
        assertEquals(UpdateVerdict.None, verdictOf(installed = "0.1.0", gateEnabled = false))
    }

    @Test
    fun noConfigMeansNoGateRatherThanABlockedApp() {
        assertEquals(UpdateVerdict.None, verdictOf(installed = "0.1.0", config = null))
    }

    @Test
    fun anUnconfiguredStoreLinkKeepsTheGateInert() {
        assertEquals(UpdateVerdict.None, verdictOf(installed = "0.1.0", storeUrl = ""))
    }

    @Test
    fun anUnreadableVersionAnywhereResolvesToNoGate() {
        assertEquals(UpdateVerdict.None, verdictOf(installed = null))
        assertEquals(UpdateVerdict.None, verdictOf(installed = "nightly"))
        assertEquals(UpdateVerdict.None, verdictOf(config = config("x", "2.0.0")))
        assertEquals(UpdateVerdict.None, verdictOf(config = config("1.0.0", "x")))
    }
}
