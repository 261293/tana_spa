package com.tana.spa.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminSeederTest {

    private class CountingHasher : PasswordHasher {
        var hashes = 0
        override fun hash(password: String): String {
            hashes++
            return "fake:$password"
        }
        override fun verify(password: String, stored: String) = stored == "fake:$password"
    }

    private val users = InMemoryUserRepository()
    private val hasher = CountingHasher()

    @Test
    fun bothDefaultAdministratorsAreCreatedAndCanSignIn() {
        seedAdmins(users, hasher)

        assertEquals(2, DEFAULT_ADMINS.size)
        for (admin in DEFAULT_ADMINS) {
            val created = assertNotNull(users.findByEmail(admin.email), admin.email)
            assertEquals(UserRole.ADMIN, created.role)
            assertTrue(hasher.verify(admin.password, assertNotNull(created.passwordHash)))
            assertNull(created.phone)
        }
    }

    @Test
    fun aSecondStartUpChangesNothingAndHashesNothing() {
        seedAdmins(users, hasher)
        val before = DEFAULT_ADMINS.map { users.findByEmail(it.email) }
        val hashesAfterFirstRun = hasher.hashes

        seedAdmins(users, hasher)

        assertEquals(before, DEFAULT_ADMINS.map { users.findByEmail(it.email) })
        assertEquals(hashesAfterFirstRun, hasher.hashes, "PBKDF2 must not run when there is nothing to create")
    }

    @Test
    fun anAddressSomebodyElseRegisteredIsLeftAloneRatherThanPromoted() {
        val seed = SeedAdmin("taken@tana.local", "Ops", "admin-password")
        val existing = users.create(seed.email, "Lan", "+84333433969", "fake:password123")
        assertTrue(existing is CreateUserResult.Created)

        seedAdmins(users, hasher, listOf(seed))

        val user = assertNotNull(users.findByEmail(seed.email))
        assertEquals(UserRole.CUSTOMER, user.role, "silently promoting an account would be a privilege escalation")
        assertEquals("Lan", user.displayName)
    }

    @Test
    fun seedingIsDrivenByTheListSoATestCanUseItsOwnAdministrator() {
        seedAdmins(users, hasher, listOf(SeedAdmin("only@tana.local", "Only", "pw")))

        assertNotNull(users.findByEmail("only@tana.local"))
        assertNull(users.findByEmail(DEFAULT_ADMINS.first().email))
    }
}
