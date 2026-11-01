package com.tana.spa.auth

import com.tana.spa.db.TestDatabase
import java.sql.SQLException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** The production store, against the real table and the constraints `V1` puts on it. */
class PostgresRefreshTokenRepositoryTest {

    private val dataSource = TestDatabase.dataSource
    private val repo = PostgresRefreshTokenRepository(dataSource)
    private val users = PostgresUserRepository(dataSource)

    private var phoneSeq = (System.nanoTime() % 90_000_000L).toInt()
    private fun email() = "t${UUID.randomUUID().toString().take(10)}@example.com"
    private fun phone() = "+843" + (10_000_000 + phoneSeq++).toString().padStart(8, '0').takeLast(8)

    private fun newUser(): User = assertIs<CreateUserResult.Created>(users.create(email(), "Lan", phone(), "hash")).user

    private fun hashOf(token: String) = hash(token)

    private val now = System.currentTimeMillis()

    @Test
    fun a_stored_token_is_found_by_its_hash_and_nothing_else() {
        val user = newUser()
        val id = repo.insert(user.id, UUID.randomUUID().toString(), hashOf("secret"), now + DAY_MILLIS)

        val found = assertNotNull(repo.find(hashOf("secret")))

        assertEquals(id, found.id)
        assertEquals(user.id, found.userId)
        assertFalse(found.revoked)
        assertFalse(found.replaced)
        assertNull(repo.find(hashOf("a different secret")))
    }

    @Test
    fun the_expiry_survives_the_round_trip_through_postgres() {
        val user = newUser()
        val expiresAt = now + 30 * DAY_MILLIS
        repo.insert(user.id, UUID.randomUUID().toString(), hashOf("when"), expiresAt)

        assertEquals(expiresAt, assertNotNull(repo.find(hashOf("when"))).expiresAtMillis)
    }

    @Test
    fun replacing_marks_the_old_row_without_deleting_it() {
        val user = newUser()
        val family = UUID.randomUUID().toString()
        val first = repo.insert(user.id, family, hashOf("first"), now + DAY_MILLIS)
        val second = repo.insert(user.id, family, hashOf("second"), now + DAY_MILLIS)

        repo.markReplaced(first, second)

        assertTrue(assertNotNull(repo.find(hashOf("first"))).replaced, "a deleted row could not be caught being replayed")
        assertFalse(assertNotNull(repo.find(hashOf("second"))).replaced)
    }

    @Test
    fun revoking_a_family_takes_every_token_in_it_and_leaves_other_families_alone() {
        val user = newUser()
        val family = UUID.randomUUID().toString()
        repo.insert(user.id, family, hashOf("one"), now + DAY_MILLIS)
        repo.insert(user.id, family, hashOf("two"), now + DAY_MILLIS)
        val other = UUID.randomUUID().toString()
        repo.insert(user.id, other, hashOf("elsewhere"), now + DAY_MILLIS)

        repo.revokeFamily(family)

        assertTrue(assertNotNull(repo.find(hashOf("one"))).revoked)
        assertTrue(assertNotNull(repo.find(hashOf("two"))).revoked)
        assertFalse(assertNotNull(repo.find(hashOf("elsewhere"))).revoked)
    }

    @Test
    fun the_sweep_drops_expired_rows_and_keeps_live_ones() {
        val user = newUser()
        // The table's own `refresh_tokens_expiry` check refuses a row that is born expired, so the
        // sweep is pointed past the shorter-lived one instead of backdating it.
        repo.insert(user.id, UUID.randomUUID().toString(), hashOf("stale"), now + DAY_MILLIS)
        repo.insert(user.id, UUID.randomUUID().toString(), hashOf("live"), now + 10 * DAY_MILLIS)

        repo.deleteExpiredBefore(now + 2 * DAY_MILLIS)

        assertNull(repo.find(hashOf("stale")))
        assertNotNull(repo.find(hashOf("live")))
    }

    @Test
    fun the_table_refuses_a_token_that_is_born_expired() {
        val user = newUser()

        assertFailsWith<SQLException> {
            repo.insert(user.id, UUID.randomUUID().toString(), hashOf("already gone"), now - DAY_MILLIS)
        }
    }

    @Test
    fun the_table_refuses_a_hash_that_is_not_sha256_sized() {
        val user = newUser()

        assertFailsWith<SQLException> {
            repo.insert(user.id, UUID.randomUUID().toString(), ByteArray(16), now + DAY_MILLIS)
        }
    }

    @Test
    fun deleting_the_account_takes_its_tokens_with_it() {
        val user = newUser()
        repo.insert(user.id, UUID.randomUUID().toString(), hashOf("owned"), now + DAY_MILLIS)

        dataSource.connection.use { c ->
            c.prepareStatement("DELETE FROM users WHERE id = ?").use { ps ->
                ps.setObject(1, UUID.fromString(user.id))
                ps.executeUpdate()
            }
        }

        assertNull(repo.find(hashOf("owned")))
    }

    @Test
    fun the_service_rotates_and_catches_a_replay_against_the_real_table() {
        val user = newUser()
        var issued = 0
        val service = RefreshTokenService(
            tokens = repo,
            users = users,
            ttlMillis = 30 * DAY_MILLIS,
            newToken = { "pg-${UUID.randomUUID()}-${issued++}" },
        )
        val first = service.issue(user.id)

        val rotated = assertIs<RotateOutcome.Rotated>(service.rotate(first.token))
        assertEquals(user.id, rotated.user.id)

        assertIs<RotateOutcome.Reused>(service.rotate(first.token))
        assertIs<RotateOutcome.Rejected>(service.rotate(rotated.refresh.token))
    }
}
