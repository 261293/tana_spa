package com.tana.spa.auth.otp

import com.tana.spa.auth.CreateUserResult
import com.tana.spa.auth.PostgresUserRepository
import com.tana.spa.db.TestDatabase
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresPhoneVerificationRepositoryTest {

    private val dataSource = TestDatabase.dataSource
    private val repo = PostgresPhoneVerificationRepository(dataSource)
    private val users = PostgresUserRepository(dataSource)
    private val t0 = Instant.parse("2026-10-06T08:00:00Z")

    private fun newUser(): String {
        val id = UUID.randomUUID().toString().take(10)
        val phone = "+84" + "3" + (10_000_000 + (System.nanoTime() % 80_000_000L)).toString().takeLast(8)
        return assertIs<CreateUserResult.Created>(users.create("$id@example.com", "Lan", phone, "h")).user.id
    }

    private fun pending(userId: String, at: Instant = t0, phone: String = "+84333433969", ip: String? = "203.0.113.9") =
        repo.createPending(userId, phone, "VN", "dev", ip, at, at + Duration.ofMinutes(10))

    @Test
    fun aNewCodeCancelsThePreviousOneSoOnlyOneIsEverLive() {
        val user = newUser()
        val first = assertNotNull(pending(user))
        val second = assertNotNull(pending(user, t0 + Duration.ofMinutes(2)))

        assertEquals(second.id, repo.findPending(user)?.id)
        val statuses = dataSource.connection.use { c ->
            c.prepareStatement("SELECT id::text, status FROM phone_verifications WHERE user_id = ?::uuid").use { ps ->
                ps.setString(1, user)
                ps.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getString(1), rs.getString(2)) } }
            }
        }
        assertEquals("canceled", statuses[first.id])
        assertEquals("pending", statuses[second.id])
    }

    @Test
    fun theStoredRowRoundTripsWithoutAnyCode() {
        val user = newUser()
        val created = assertNotNull(pending(user))
        assertEquals(created, repo.findPending(user))
        assertEquals("VN", created.region)
        assertEquals(VerificationStatus.PENDING, created.status)
        assertEquals(0, created.attempts)
        assertEquals(t0, created.sentAt)
        assertEquals(t0 + Duration.ofMinutes(10), created.expiresAt)
    }

    @Test
    fun sendStatsCountByUserNumberAndAddressWithinTheirWindows() {
        val user = newUser()
        val other = newUser()
        val phone = "+84333${(100_000 + System.nanoTime() % 800_000)}"
        val ip = "198.51.100.${(System.nanoTime() % 200).toInt() + 1}"
        pending(user, t0 - Duration.ofHours(30), phone, ip)            // outside every window
        pending(user, t0 - Duration.ofHours(5), phone, ip)             // inside the day only
        pending(user, t0 - Duration.ofMinutes(30), phone, ip)          // inside the hour
        pending(other, t0 - Duration.ofMinutes(10), phone, ip)         // other user, same number and address

        val stats = repo.sendStats(user, phone, ip, hourAgo = t0 - Duration.ofHours(1), dayAgo = t0 - Duration.ofHours(24))

        assertEquals(t0 - Duration.ofMinutes(30), stats.lastSentAt)
        assertEquals(2, stats.phoneSendsInWindow)   // user@-30m and other@-10m
        assertEquals(2, stats.userSendsInDay)       // user@-5h and user@-30m
        assertEquals(2, stats.ipSendsInWindow)
    }

    @Test
    fun anUnknownAddressCountsAsZeroNotAsEveryone() {
        val user = newUser()
        pending(user, ip = null)
        assertEquals(0, repo.sendStats(user, "+84333433969", null, t0 - Duration.ofHours(1), t0 - Duration.ofDays(1)).ipSendsInWindow)
    }

    @Test
    fun consumeAttemptStopsAtTheCapEvenUnderConcurrentGuessing() {
        val user = newUser()
        val row = assertNotNull(pending(user))
        val threads = 16
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val results = List(threads) { pool.submit<Int?> { start.await(); repo.consumeAttempt(row.id, 5) } }
        start.countDown()
        val spent = results.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(5, spent.count { it != null }, "exactly the cap may be spent, never more")
        assertEquals(setOf(1, 2, 3, 4, 5), spent.filterNotNull().toSet())
    }

    @Test
    fun consumeAttemptRefusesOnceTheCodeIsNoLongerPending() {
        val user = newUser()
        val row = assertNotNull(pending(user))
        repo.cancel(row.id)
        assertNull(repo.consumeAttempt(row.id, 5))
        assertNull(repo.consumeAttempt("not-a-uuid", 5))
    }

    @Test
    fun approvalStampsTheTimeAndEndsTheCode() {
        val user = newUser()
        val row = assertNotNull(pending(user))
        repo.markApproved(row.id, t0 + Duration.ofMinutes(1))

        assertNull(repo.findPending(user))
        val stamped = dataSource.connection.use { c ->
            c.prepareStatement("SELECT status, verified_at IS NOT NULL FROM phone_verifications WHERE id = ?::uuid").use { ps ->
                ps.setString(1, row.id)
                ps.executeQuery().use { rs -> rs.next(); rs.getString(1) to rs.getBoolean(2) }
            }
        }
        assertEquals("approved" to true, stamped)
    }

    @Test
    fun expiredAndFailedTransitionsAreRecorded() {
        val user = newUser()
        val expired = assertNotNull(pending(user))
        repo.markExpired(expired.id)
        assertNull(repo.findPending(user))

        val failed = assertNotNull(pending(user, t0 + Duration.ofMinutes(5)))
        repo.markFailed(failed.id)
        assertNull(repo.findPending(user))
    }

    @Test
    fun parallelSendsLeaveExactlyOneLiveCode() {
        val user = newUser()
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val results = List(threads) { i -> pool.submit<PhoneVerification?> { start.await(); pending(user, t0 + Duration.ofSeconds(i.toLong())) } }
        start.countDown()
        val created = results.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertTrue(created.any { it != null }, "at least one request must win")
        val live = dataSource.connection.use { c ->
            c.prepareStatement("SELECT count(*) FROM phone_verifications WHERE user_id = ?::uuid AND status = 'pending'").use { ps ->
                ps.setString(1, user)
                ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
            }
        }
        assertEquals(1, live)
    }

    @Test
    fun malformedIdsNeverThrow() {
        assertNull(repo.findPending("nope"))
        assertNull(pending("nope"))
        repo.markFailed("nope")
        repo.cancel("nope")
        repo.setProviderRef("nope", "x")
    }
}
