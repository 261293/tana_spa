package com.tana.spa.auth

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountSeederTest {

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

    private val configured = listOf(
        SeedAccount("ops@tana.local", "Ops", "ops-password", UserRole.ADMIN),
        SeedAccount("owner@tana.local", "Jade Bathhouse", "owner-password", UserRole.OWNER),
    )

    private fun env(vararg pairs: Pair<String, String>): (String) -> String? = pairs.toMap()::get

    @Test
    fun configured_accounts_are_created_with_their_role_and_can_sign_in() {
        seedAccounts(users, hasher, configured)

        for (account in configured) {
            val created = assertNotNull(users.findByEmail(account.email), account.email)
            assertEquals(account.role, created.role)
            assertTrue(hasher.verify(account.password, assertNotNull(created.passwordHash)))
            assertNull(created.phone)
        }
    }

    @Test
    fun a_second_start_up_changes_nothing_and_hashes_nothing() {
        seedAccounts(users, hasher, configured)
        val before = configured.map { users.findByEmail(it.email) }
        val hashesAfterFirstRun = hasher.hashes

        seedAccounts(users, hasher, configured)

        assertEquals(before, configured.map { users.findByEmail(it.email) })
        assertEquals(hashesAfterFirstRun, hasher.hashes, "PBKDF2 must not run when there is nothing to create")
    }

    @Test
    fun an_address_somebody_else_registered_is_left_alone_rather_than_promoted() {
        val seed = SeedAccount("taken@tana.local", "Ops", "owner-password", UserRole.OWNER)
        assertTrue(users.create(seed.email, "Lan", "+84333433969", "fake:password123") is CreateUserResult.Created)

        seedAccounts(users, hasher, listOf(seed))

        val user = assertNotNull(users.findByEmail(seed.email))
        assertEquals(UserRole.CUSTOMER, user.role, "silently promoting an account would be a privilege escalation")
        assertEquals("Lan", user.displayName)
    }

    @Test
    fun the_two_spa_administrators_are_seeded_whatever_the_environment_says() {
        val fromEmptyEnvironment = SeedAccounts.fromEnvironment(production = false, env = env()).accounts
        val fromConfiguredEnvironment = SeedAccounts.fromEnvironment(
            production = true,
            env = env(
                SeedAccounts.ADMIN_PASSWORD_ENV to "fixed-password",
                SeedAccounts.OWNER_ENV to "owner@tana.local|Jade Bathhouse|owner-password",
            ),
        ).accounts

        for (accounts in listOf(fromEmptyEnvironment, fromConfiguredEnvironment)) {
            assertEquals(
                listOf("sanghtruong@tana.local", "duchnguyen@tana.local"),
                accounts.filter { it.role == UserRole.ADMIN }.map { it.email },
            )
        }
        assertTrue(fromConfiguredEnvironment.filter { it.role == UserRole.ADMIN }.all { it.password == "fixed-password" })
    }

    @Test
    fun the_environment_adds_more_administrators_and_the_owner() {
        val accounts = SeedAccounts.fromEnvironment(
            production = true,
            env = env(
                SeedAccounts.ADMIN_PASSWORD_ENV to "fixed-password",
                SeedAccounts.ADMINS_ENV to "Ops@tana.local|Ops|first-password; second@tana.local|Second|second-password",
                SeedAccounts.OWNER_ENV to "owner@tana.local|Jade Bathhouse|owner-password",
            ),
        ).accounts

        assertEquals(
            listOf("sanghtruong@tana.local", "duchnguyen@tana.local", "ops@tana.local", "second@tana.local", "owner@tana.local"),
            accounts.map { it.email },
            "addresses are compared lowercased everywhere else",
        )
        assertEquals(UserRole.OWNER, accounts.last().role)
    }

    @Test
    fun production_refuses_to_start_without_an_owner_or_an_administrator_password() {
        val missingOwner = assertFailsWith<IllegalArgumentException> {
            SeedAccounts.fromEnvironment(production = true, env = env(SeedAccounts.ADMIN_PASSWORD_ENV to "fixed-password"))
        }
        assertContains(missingOwner.message.orEmpty(), SeedAccounts.OWNER_ENV)

        val missingPassword = assertFailsWith<IllegalArgumentException> {
            SeedAccounts.fromEnvironment(production = true, env = env(SeedAccounts.OWNER_ENV to "o@tana.local|O|password"))
        }
        assertContains(missingPassword.message.orEmpty(), SeedAccounts.ADMIN_PASSWORD_ENV)
    }

    @Test
    fun a_developer_checkout_gets_an_owner_and_a_password_that_is_not_in_the_repository() {
        val accounts = SeedAccounts.fromEnvironment(production = false, env = env()).accounts

        val owner = assertNotNull(accounts.singleOrNull { it.role == UserRole.OWNER })
        assertTrue(owner.password.length >= 20, "a generated password must not be guessable")
        assertTrue(accounts.first().password.length >= 20)
    }

    @Test
    fun a_malformed_entry_is_skipped_rather_than_seeded_half_configured() {
        val accounts = SeedAccounts.fromEnvironment(
            production = false,
            env = env(SeedAccounts.ADMINS_ENV to "ops@tana.local|Ops;broken-entry;good@tana.local|Good|password"),
        ).accounts

        assertEquals(
            listOf("sanghtruong@tana.local", "duchnguyen@tana.local", "good@tana.local"),
            accounts.filter { it.role == UserRole.ADMIN }.map { it.email },
        )
    }
}
