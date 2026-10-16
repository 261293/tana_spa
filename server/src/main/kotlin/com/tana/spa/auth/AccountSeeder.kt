package com.tana.spa.auth

import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64

private val log = LoggerFactory.getLogger("com.tana.spa.auth.AccountSeeder")

data class SeedAccount(val email: String, val displayName: String, val password: String, val role: UserRole)

/** The spa's own two administrators. They are seeded on every server, whatever the environment says. */
private val FIXED_ADMINS = listOf(
    "sanghtruong@tana.local" to "Sang H Truong",
    "duchnguyen@tana.local" to "Duc H Nguyen",
)

/**
 * The privileged accounts created at start-up: the two administrators above, plus whatever the
 * environment adds.
 *
 * ```
 * SEED_ADMIN_PASSWORD=<password for the two fixed administrators>
 * SEED_ADMINS=ops@tana.local|Ops|<password>;second@tana.local|Second|<password>
 * SEED_OWNER=owner@tana.local|Jade Bathhouse|<password>
 * ```
 *
 * The owner runs the spa and is required in production. Outside production a missing password is
 * generated per start and logged once, so a fresh checkout can still sign in without a password
 * living in source control.
 */
data class SeedAccounts(val accounts: List<SeedAccount>) {
    companion object {
        const val ADMIN_PASSWORD_ENV = "SEED_ADMIN_PASSWORD"
        const val ADMINS_ENV = "SEED_ADMINS"
        const val OWNER_ENV = "SEED_OWNER"

        private const val DEV_OWNER_EMAIL = "owner@tana.local"

        fun fromEnvironment(
            production: Boolean = System.getenv("APP_ENV") == "production",
            env: (String) -> String? = System::getenv,
        ): SeedAccounts {
            val extraAdmins = parse(env(ADMINS_ENV), UserRole.ADMIN)
            val owners = parse(env(OWNER_ENV), UserRole.OWNER)
            if (production) {
                require(owners.isNotEmpty()) { "$OWNER_ENV is required when APP_ENV=production" }
                require(!env(ADMIN_PASSWORD_ENV).isNullOrBlank()) { "$ADMIN_PASSWORD_ENV is required when APP_ENV=production" }
            }
            val adminPassword = env(ADMIN_PASSWORD_ENV)?.takeIf { it.isNotBlank() }
                ?: generated("the two fixed administrators")
            return SeedAccounts(
                FIXED_ADMINS.map { (email, name) -> SeedAccount(email, name, adminPassword, UserRole.ADMIN) } +
                    extraAdmins +
                    owners.ifEmpty { listOf(SeedAccount(DEV_OWNER_EMAIL, "Local Owner", generated(DEV_OWNER_EMAIL), UserRole.OWNER)) },
            )
        }

        /** `email|Display Name|password`, several separated by `;`. Incomplete entries are skipped. */
        private fun parse(raw: String?, role: UserRole): List<SeedAccount> = raw.orEmpty()
            .split(';')
            .mapNotNull { entry -> entry.split('|').map { it.trim() }.takeIf { it.size == 3 && it.none(String::isEmpty) } }
            .map { (email, name, password) -> SeedAccount(email.lowercase(), name, password, role) }

        // Printed once, to the server log: a developer needs a way in, and a password that only
        // exists for this run cannot leak from the repository.
        private fun generated(who: String): String {
            val password = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(18).also(SecureRandom()::nextBytes))
            log.warn("Nothing configured for {}: seeding with the generated password {}", who, password)
            return password
        }
    }
}

/** Idempotent, so it runs on every start-up. An existing account is never promoted. */
fun seedAccounts(users: UserRepository, hasher: PasswordHasher, accounts: List<SeedAccount>) {
    accounts.filterNot { users.alreadyHolds(it) }.forEach { users.seed(it, hasher) }
}

private fun UserRepository.alreadyHolds(account: SeedAccount): Boolean {
    val existing = findByEmail(account.email) ?: return false
    if (existing.role != account.role) {
        // Somebody registered this address as a customer; promoting it would be a privilege
        // escalation nobody asked for.
        log.warn("{} exists but its role is {}, not {}: leaving it alone", account.email, existing.role.sql, account.role.sql)
    }
    return true
}

// Hashing is deliberately slow (PBKDF2), so it only runs for an account that is actually missing.
private fun UserRepository.seed(account: SeedAccount, hasher: PasswordHasher) {
    val created = ensureSeeded(account.email, account.displayName, hasher.hash(account.password), account.role)
    if (created == null) {
        log.error("Could not create the {} {}", account.role.sql, account.email)
    } else {
        log.info("Created {} {} ({})", account.role.sql, created.email, created.id)
    }
}
