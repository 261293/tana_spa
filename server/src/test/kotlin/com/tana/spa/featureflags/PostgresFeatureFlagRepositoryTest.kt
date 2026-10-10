package com.tana.spa.featureflags

import com.tana.spa.auth.CreateUserResult
import com.tana.spa.auth.PostgresUserRepository
import com.tana.spa.db.TestDatabase
import com.tana.spa.shared.featureflags.FeatureFlag
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresFeatureFlagRepositoryTest {

    private val dataSource = TestDatabase.dataSource
    private val repo = PostgresFeatureFlagRepository(dataSource)
    private val users = PostgresUserRepository(dataSource)

    private fun newUser(): String {
        val id = UUID.randomUUID().toString().take(10)
        val phone = "+843" + (10_000_000 + (System.nanoTime() % 80_000_000L)).toString().takeLast(8)
        return assertIs<CreateUserResult.Created>(users.create("$id@example.com", "Lan", phone, "h")).user.id
    }

    /** A key that satisfies the schema's format check without colliding with another test. */
    private fun newKey() = "test_flag_" + UUID.randomUUID().toString().replace("-", "").take(12)

    @Test
    fun theMigrationShipsPhoneVerificationSwitchedOff() {
        assertEquals(false, repo.globals()[FeatureFlag.PHONE_OTP_VERIFICATION.key])
    }

    @Test
    fun aGlobalStateIsStoredAndOverwrittenByTheNextWrite() {
        val key = newKey()

        repo.setGlobal(key, enabled = true, description = "first", updatedBy = "test")
        assertEquals(true, repo.globals()[key])

        repo.setGlobal(key, enabled = false, description = null, updatedBy = "test")
        assertEquals(false, repo.globals()[key])

        assertEquals("first", description(key))
    }

    @Test
    fun theAuditTrailRecordsWhoChangedTheFlag() {
        val key = newKey()
        val user = newUser()

        repo.setGlobal(key, enabled = true, description = null, updatedBy = user)
        repo.setOverride(key, user, enabled = true, note = null, updatedBy = user)

        // The id, not the email: updated_by is capped at 100 characters and an email may be 254.
        assertEquals(user, column("SELECT updated_by FROM feature_flags WHERE key = ?", key))
        assertEquals(
            user,
            column("SELECT updated_by FROM feature_flag_overrides WHERE flag_key = ?", key),
        )
    }

    @Test
    fun clearingReportsWhetherThereWasAnythingToClear() {
        val key = newKey()
        repo.setGlobal(key, enabled = true, description = null, updatedBy = "test")

        assertTrue(repo.clearGlobal(key))
        assertFalse(repo.clearGlobal(key))
        assertNull(repo.globals()[key])
    }

    @Test
    fun anOverrideIsPerUserAndPerFlag() {
        val key = newKey()
        val one = newUser()
        val other = newUser()

        repo.setOverride(key, one, enabled = true, note = "tester", updatedBy = "test")

        assertEquals(mapOf(key to true), repo.overridesFor(one))
        assertTrue(repo.overridesFor(other).isEmpty())
        assertTrue(repo.allOverrides().any { it.key == key && it.userId == one && it.note == "tester" })
    }

    @Test
    fun writingAnOverrideTwiceUpdatesItInsteadOfFailing() {
        val key = newKey()
        val user = newUser()

        repo.setOverride(key, user, enabled = true, note = "first", updatedBy = "test")
        repo.setOverride(key, user, enabled = false, note = null, updatedBy = "test")

        assertEquals(mapOf(key to false), repo.overridesFor(user))
        assertEquals("first", repo.allOverrides().single { it.key == key && it.userId == user }.note)
    }

    @Test
    fun anOverrideCanBeSetForAFlagThatHasNoGlobalRow() {
        val key = newKey()
        val user = newUser()

        repo.setOverride(key, user, enabled = true, note = null, updatedBy = "test")

        assertEquals(mapOf(key to true), repo.overridesFor(user))
        assertNull(repo.globals()[key])
    }

    @Test
    fun clearingAnOverrideReportsWhetherOneExisted() {
        val key = newKey()
        val user = newUser()
        repo.setOverride(key, user, enabled = true, note = null, updatedBy = "test")

        assertTrue(repo.clearOverride(key, user))
        assertFalse(repo.clearOverride(key, user))
    }

    @Test
    fun anIdThatIsNotAUuidIsAnEmptyAnswerRatherThanAnError() {
        assertTrue(repo.overridesFor("not-a-uuid").isEmpty())
        assertFalse(repo.clearOverride(newKey(), "not-a-uuid"))
    }

    @Test
    fun deletingAUserTakesTheirOverridesWithThem() {
        val key = newKey()
        val user = newUser()
        repo.setOverride(key, user, enabled = true, note = null, updatedBy = "test")

        dataSource.connection.use { c ->
            c.prepareStatement("DELETE FROM users WHERE id = ?::uuid").use { ps ->
                ps.setString(1, user)
                ps.executeUpdate()
            }
        }

        assertTrue(repo.overridesFor(user).isEmpty())
        assertFalse(repo.allOverrides().any { it.userId == user })
    }

    private fun description(key: String): String? = column("SELECT description FROM feature_flags WHERE key = ?", key)

    /** First column of the first row, for the bookkeeping the repository interface does not expose. */
    private fun column(sql: String, key: String): String? = dataSource.connection.use { c ->
        c.prepareStatement(sql).use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }
}
