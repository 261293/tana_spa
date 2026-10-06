package com.tana.spa.auth

import com.tana.spa.db.TestDatabase
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

class PostgresUserRepositoryTest {

    private val dataSource = TestDatabase.dataSource
    private val repo = PostgresUserRepository(dataSource)

    private var phoneSeq = (System.nanoTime() % 90_000_000L).toInt()
    private fun email() = "u${UUID.randomUUID().toString().take(10)}@example.com"
    private fun phone() = "+84" + "3" + (10_000_000 + phoneSeq++).toString().padStart(8, '0').takeLast(8)

    private fun create(email: String = email(), phone: String = phone()) = repo.create(email, "Lan", phone, "hash")

    private fun sql(query: String, vararg params: Any?) = dataSource.connection.use { c ->
        c.prepareStatement(query).use { ps ->
            params.forEachIndexed { i, p -> ps.setObject(i + 1, p) }
            ps.executeUpdate()
        }
    }

    @Test
    fun createdUserCanBeFoundByEmailAndId() {
        val email = email()
        val phone = phone()
        val created = assertIs<CreateUserResult.Created>(repo.create(email, "Nguyễn Lê Phương Anh", phone, "h"))

        val byEmail = assertNotNull(repo.findByEmail(email))
        assertEquals(created.user, byEmail)
        assertEquals("Nguyễn Lê Phương Anh", byEmail.displayName) // diacritics survive the round trip
        assertEquals(phone, byEmail.phone)
        assertEquals(created.user, repo.findById(created.user.id))
    }

    @Test
    fun unknownAndMalformedLookupsReturnNull() {
        assertNull(repo.findByEmail(email()))
        assertNull(repo.findById(UUID.randomUUID().toString()))
        assertNull(repo.findById("not-a-uuid"))
        repo.recordLogin("not-a-uuid") // must not throw
    }

    @Test
    fun duplicateEmailIsReported() {
        val email = email()
        assertIs<CreateUserResult.Created>(create(email))
        assertEquals(CreateUserResult.EmailTaken, create(email = email))
    }

    @Test
    fun anUnverifiedPhoneDoesNotBlockOthersButAVerifiedOneDoes() {
        val phone = phone()
        val first = assertIs<CreateUserResult.Created>(create(phone = phone)).user
        // Unverified: a stranger registering someone else's number must not lock the real owner out.
        val second = assertIs<CreateUserResult.Created>(create(phone = phone)).user
        assertNull(repo.verifiedPhoneOwner(phone))

        assertEquals(PhoneVerifiedResult.VERIFIED, repo.markPhoneVerified(first.id, phone))
        assertEquals(first.id, repo.verifiedPhoneOwner(phone))
        assertEquals(true, repo.findById(first.id)?.phoneVerified)

        // Now the number is owned: new sign-ups are refused, and the late claimant loses.
        assertEquals(CreateUserResult.PhoneTaken, create(phone = phone))
        assertEquals(PhoneVerifiedResult.CLAIMED_BY_ANOTHER, repo.markPhoneVerified(second.id, phone))
        assertEquals(false, repo.findById(second.id)?.phoneVerified)
    }

    @Test
    fun verifyingIsIdempotentAndRefusesAStalePhone() {
        val user = assertIs<CreateUserResult.Created>(create()).user
        assertEquals(PhoneVerifiedResult.PHONE_CHANGED, repo.markPhoneVerified(user.id, "+84999999999"))
        val phone = checkNotNull(user.phone)
        assertEquals(PhoneVerifiedResult.VERIFIED, repo.markPhoneVerified(user.id, phone))
        assertEquals(PhoneVerifiedResult.VERIFIED, repo.markPhoneVerified(user.id, phone))
        assertEquals(PhoneVerifiedResult.PHONE_CHANGED, repo.markPhoneVerified("not-a-uuid", phone))
    }

    @Test
    fun onlyOneOfManyConcurrentVerifiersWinsTheNumber() {
        val phone = phone()
        val users = List(6) { assertIs<CreateUserResult.Created>(create(phone = phone)).user }
        val pool = Executors.newFixedThreadPool(users.size)
        val start = CountDownLatch(1)
        val results = users.map { u ->
            pool.submit<PhoneVerifiedResult> {
                start.await()
                repo.markPhoneVerified(u.id, phone)
            }
        }
        start.countDown()
        val outcomes = results.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, outcomes.count { it == PhoneVerifiedResult.VERIFIED })
        assertEquals(users.size - 1, outcomes.count { it == PhoneVerifiedResult.CLAIMED_BY_ANOTHER })
    }

    @Test
    fun softDeletedUserIsInvisibleAndFreesItsEmailAndPhone() {
        val email = email()
        val phone = phone()
        val first = assertIs<CreateUserResult.Created>(create(email, phone)).user

        sql("UPDATE users SET deleted_at = now() WHERE id = ?", UUID.fromString(first.id))
        assertNull(repo.findByEmail(email))
        assertNull(repo.findById(first.id))

        val second = assertIs<CreateUserResult.Created>(create(email, phone)).user
        assertEquals(second, repo.findByEmail(email))
    }

    @Test
    fun suspendedUsersCannotBeFound() {
        val user = assertIs<CreateUserResult.Created>(create()).user
        sql("UPDATE users SET status = 'suspended' WHERE id = ?", UUID.fromString(user.id))
        assertNull(repo.findByEmail(user.email))
        assertNull(repo.findById(user.id))
    }

    @Test
    fun concurrentSignUpsForTheSameEmailYieldExactlyOneAccount() {
        val email = email()
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val results = (1..threads).map {
            val phone = phone() // distinct phones: only the email can conflict
            pool.submit<CreateUserResult> {
                start.await()
                create(email, phone)
            }
        }
        start.countDown()
        val outcomes = results.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, outcomes.count { it is CreateUserResult.Created })
        assertEquals(threads - 1, outcomes.count { it == CreateUserResult.EmailTaken })
    }

    @Test
    fun referralCodeCollisionIsRetriedWithAFreshCode() {
        val shared = "SHARED" + UUID.randomUUID().toString().take(4).uppercase()
        val unique = "UNIQ" + UUID.randomUUID().toString().take(6).uppercase()
        val codes = ArrayDeque(listOf(shared, shared, unique))
        val repo = PostgresUserRepository(dataSource) { codes.removeFirst() }

        assertIs<CreateUserResult.Created>(repo.create(email(), "A", phone(), "h")) // takes `shared`
        assertIs<CreateUserResult.Created>(repo.create(email(), "B", phone(), "h")) // collides, retries with `unique`
        assertTrue(codes.isEmpty(), "both retry codes should have been consumed")
    }

    @Test
    fun recordLoginStampsLastLogin() {
        val user = assertIs<CreateUserResult.Created>(create()).user
        fun lastLogin(): Boolean = dataSource.connection.use { c ->
            c.prepareStatement("SELECT last_login_at IS NOT NULL FROM users WHERE id = ?").use { ps ->
                ps.setObject(1, UUID.fromString(user.id))
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getBoolean(1)
                }
            }
        }
        assertEquals(false, lastLogin())
        repo.recordLogin(user.id)
        assertEquals(true, lastLogin())
    }

    @Test
    fun generatedReferralCodesAreWellFormed() {
        val codes = List(200) { randomReferralCode() }
        assertTrue(codes.all { it.length == 10 && it.all { c -> c in "ABCDEFGHJKMNPQRSTUVWXYZ23456789" } })
        assertTrue(codes.toSet().size > 190, "codes should be effectively unique")
    }
}
