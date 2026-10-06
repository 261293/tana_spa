package com.tana.spa.auth

import java.util.UUID

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    /** Normalised `+<country><number>`. */
    val phone: String?,
    /** True once the owner proved control of [phone] with a one-time code. */
    val phoneVerified: Boolean,
    /** Null for accounts that only ever signed in with a social provider. */
    val passwordHash: String?,
)

sealed interface CreateUserResult {
    data class Created(val user: User) : CreateUserResult
    data object EmailTaken : CreateUserResult

    /** A *verified* account already owns this number. Unverified claims do not block (see V5). */
    data object PhoneTaken : CreateUserResult
}

enum class PhoneVerifiedResult {
    VERIFIED,

    /** The account's number changed (or the account vanished) while the code was outstanding. */
    PHONE_CHANGED,

    /** Someone else proved ownership of this number first. */
    CLAIMED_BY_ANOTHER,
}

/**
 * Blocking by design (JDBC is blocking); [AuthService] calls it from the IO dispatcher.
 * Only active users are visible: soft-deleted and suspended accounts are treated as absent.
 */
interface UserRepository {
    fun findByEmail(email: String): User?
    fun findById(id: String): User?

    /** Atomically creates the user, reporting which unique field (if any) was already taken. */
    fun create(email: String, displayName: String, phone: String, passwordHash: String): CreateUserResult

    fun recordLogin(id: String)

    /** The id of the account that has *verified* [phone], if any. */
    fun verifiedPhoneOwner(phone: String): String?

    /** Marks [id]'s phone verified, but only if it still equals [phone]. First verifier wins. */
    fun markPhoneVerified(id: String, phone: String): PhoneVerifiedResult
}

/**
 * In-memory store for local development and fast tests; selected automatically when
 * `DATABASE_URL` is not set. Data is lost on restart.
 */
class InMemoryUserRepository : UserRepository {
    private val byId = LinkedHashMap<String, User>()

    @Synchronized
    override fun findByEmail(email: String): User? = byId.values.firstOrNull { it.email == email }

    @Synchronized
    override fun findById(id: String): User? = byId[id]

    // Synchronized so "check then insert" is one atomic step: concurrent sign-ups for the same
    // email cannot both succeed.
    @Synchronized
    override fun create(email: String, displayName: String, phone: String, passwordHash: String): CreateUserResult {
        if (byId.values.any { it.email == email }) return CreateUserResult.EmailTaken
        if (verifiedOwner(phone) != null) return CreateUserResult.PhoneTaken
        val user = User(UUID.randomUUID().toString(), email, displayName, phone, phoneVerified = false, passwordHash)
        byId[user.id] = user
        return CreateUserResult.Created(user)
    }

    override fun recordLogin(id: String) = Unit

    @Synchronized
    override fun verifiedPhoneOwner(phone: String): String? = verifiedOwner(phone)

    @Synchronized
    override fun markPhoneVerified(id: String, phone: String): PhoneVerifiedResult {
        val user = byId[id]
        if (user == null || user.phone != phone) return PhoneVerifiedResult.PHONE_CHANGED
        val owner = verifiedOwner(phone)
        if (owner != null && owner != id) return PhoneVerifiedResult.CLAIMED_BY_ANOTHER
        byId[id] = user.copy(phoneVerified = true)
        return PhoneVerifiedResult.VERIFIED
    }

    private fun verifiedOwner(phone: String): String? =
        byId.values.firstOrNull { it.phone == phone && it.phoneVerified }?.id
}
