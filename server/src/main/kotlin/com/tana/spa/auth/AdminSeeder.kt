package com.tana.spa.auth

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.tana.spa.auth.AdminSeeder")

data class SeedAdmin(val email: String, val displayName: String, val password: String)

/**
 * These passwords are in source control, so they are public: anybody who can read this repository
 * can sign in as an administrator on a server that still has them. Change them, or delete the
 * accounts, before the server is reachable by anyone you do not trust.
 */
val DEFAULT_ADMINS: List<SeedAdmin> = listOf(
    SeedAdmin(email = "sanghtruong@tana.local", displayName = "Sang H Truong", password = "P@ssw0rd26"),
    SeedAdmin(email = "duchnguyen@tana.local", displayName = "Duc H Nguyen", password = "P@ssw0rd26"),
)

/** Idempotent, so it runs on every start-up. */
fun seedAdmins(users: UserRepository, hasher: PasswordHasher, admins: List<SeedAdmin> = DEFAULT_ADMINS) {
    admins.filterNot { users.alreadyHolds(it) }.forEach { users.createAdmin(it, hasher) }
}

private fun UserRepository.alreadyHolds(admin: SeedAdmin): Boolean {
    val existing = findByEmail(admin.email) ?: return false
    if (existing.role != UserRole.ADMIN) {
        // Somebody registered this address as a customer; promoting it would be a privilege
        // escalation nobody asked for.
        log.warn("{} exists but its role is {}, not admin: leaving it alone", admin.email, existing.role.sql)
    }
    return true
}

// Hashing is deliberately slow (PBKDF2), so it only runs for an account that is actually missing.
private fun UserRepository.createAdmin(admin: SeedAdmin, hasher: PasswordHasher) {
    val created = ensureAdmin(admin.email, admin.displayName, hasher.hash(admin.password))
    if (created == null) {
        log.error("Could not create the administrator {}", admin.email)
    } else {
        log.info("Created administrator {} ({})", created.email, created.id)
    }
}
