package com.tana.spa.featureflags

import com.tana.spa.auth.CreateUserResult
import com.tana.spa.auth.InMemoryUserRepository
import com.tana.spa.shared.featureflags.ConfigurableFeatureFlags
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureFlagServiceTest {

    // JUnit 4 rejects @Test methods that return a value, which `= runBlocking { ... }` would.
    private fun blocking(block: suspend CoroutineScope.() -> Unit) {
        runBlocking(block = block)
    }

    private val otp = FeatureFlag.PHONE_OTP_VERIFICATION
    private val repository = InMemoryFeatureFlagRepository()
    private val users = InMemoryUserRepository()

    private val userId: String = assertIs<CreateUserResult.Created>(
        users.create("lan@example.com", "Lan", "+84333433969", "hash"),
    ).user.id

    private fun service(deployDefaults: FeatureFlags = FeatureFlags.defaults) = FeatureFlagService(repository, users, deployDefaults)

    @Test
    fun withNothingStoredTheCompiledDefaultDecides() = blocking {
        assertEquals(otp.defaultEnabled, service().isEnabled(otp))
        assertEquals(otp.defaultEnabled, service().isEnabled(otp, userId))
    }

    @Test
    fun theDeployDefaultBeatsTheCompiledOne() = blocking {
        val service = service(ConfigurableFeatureFlags(mapOf(otp to true)))

        assertTrue(service.isEnabled(otp))
    }

    @Test
    fun aGlobalRowBeatsTheDeployDefault() = blocking {
        val service = service(ConfigurableFeatureFlags(mapOf(otp to true)))
        service.setGlobal(otp, enabled = false, actor = "test")

        assertFalse(service.isEnabled(otp))
        assertFalse(service.isEnabled(otp, userId))
    }

    @Test
    fun aUsersOverrideBeatsTheGlobalRow() = blocking {
        val service = service()
        service.setGlobal(otp, enabled = false, actor = "test")
        service.setForUser(otp, userId, enabled = true, note = "tester", actor = "test")

        assertTrue(service.isEnabled(otp, userId), "on for the one tester")
        assertFalse(service.isEnabled(otp), "still off for everybody else")
    }

    @Test
    fun clearingTheGlobalRowHandsTheFlagBackToTheDeployDefaultRatherThanSwitchingItOff() = blocking {
        val service = service(ConfigurableFeatureFlags(mapOf(otp to true)))
        service.setGlobal(otp, enabled = false, actor = "test")
        assertFalse(service.isEnabled(otp))

        assertEquals(FlagWriteResult.Applied, service.clearGlobal(otp, actor = "test"))

        assertTrue(service.isEnabled(otp), "no stored opinion means the deployment decides again")
    }

    @Test
    fun clearingWhatWasNeverStoredIsReportedRatherThanPretendingItWorked() = blocking {
        val service = service()

        assertEquals(FlagWriteResult.NotStored, service.clearGlobal(otp, actor = "test"))
        assertEquals(FlagWriteResult.NotStored, service.clearForUser(otp, userId, actor = "test"))
    }

    @Test
    fun anOverrideForSomebodyWhoIsNotAnAccountIsRefused() = blocking {
        val service = service()

        val result = service.setForUser(otp, "00000000-0000-0000-0000-000000000000", true, null, "test")

        assertEquals(FlagWriteResult.UnknownUser, result)
        assertTrue(repository.allOverrides().isEmpty())
    }

    @Test
    fun clearingAUsersOverrideLeavesTheGlobalRowAlone() = blocking {
        val service = service()
        service.setGlobal(otp, enabled = false, actor = "test")
        service.setForUser(otp, userId, enabled = true, note = null, actor = "test")

        assertEquals(FlagWriteResult.Applied, service.clearForUser(otp, userId, actor = "test"))

        assertFalse(service.isEnabled(otp, userId))
        assertEquals(mapOf(otp.key to false), repository.globals())
    }

    @Test
    fun resolveAllAnswersForEveryFlagInTheCatalog() = blocking {
        val service = service()
        service.setForUser(otp, userId, enabled = true, note = null, actor = "test")

        val anonymous = service.resolveAll()
        val signedIn = service.resolveAll(userId)

        assertEquals(FeatureFlag.entries.map { it.key }.toSet(), anonymous.keys)
        assertEquals(otp.defaultEnabled, anonymous[otp.key])
        assertEquals(true, signedIn[otp.key])
    }

    @Test
    fun theAdminViewSeparatesWhatIsStoredFromWhatIsMerelyTheDefault() = blocking {
        val service = service(ConfigurableFeatureFlags(mapOf(otp to true)))

        val before = service.adminView().single { it.key == otp.key }
        assertNull(before.globalEnabled, "nothing stored yet")
        assertTrue(before.deployDefault)
        assertTrue(before.enabled)
        assertTrue(before.overrides.isEmpty())
        assertEquals(otp.description, before.description)

        service.setGlobal(otp, enabled = false, actor = "test")
        service.setForUser(otp, userId, enabled = true, note = "tester", actor = "test")

        val after = service.adminView().single { it.key == otp.key }
        assertEquals(false, after.globalEnabled)
        assertFalse(after.enabled)
        assertTrue(after.deployDefault, "the deploy default is still reported, for when the row is cleared")
        assertEquals(1, after.overrides.size)
        assertEquals(userId, after.overrides.single().userId)
        assertEquals(true, after.overrides.single().enabled)
        assertEquals("tester", after.overrides.single().note)
    }
}
