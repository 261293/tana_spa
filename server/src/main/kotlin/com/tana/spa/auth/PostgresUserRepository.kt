package com.tana.spa.auth

import org.postgresql.util.PSQLException
import java.security.SecureRandom
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-backed [UserRepository] (schema: `V1`, `V5`).
 *
 * Uniqueness is decided by the database's partial unique indexes, never by a "SELECT then INSERT"
 * in code, so two simultaneous sign-ups for the same email cannot both succeed, and two people
 * cannot both end up with the same *verified* phone number.
 */
class PostgresUserRepository(private val dataSource: DataSource, private val newReferralCode: () -> String = ::randomReferralCode) :
    UserRepository {

    override fun findByEmail(email: String): User? = dataSource.connection.use { c ->
        c.prepareStatement("$SELECT WHERE email = ? AND $ACTIVE").use { ps ->
            ps.setString(1, email)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
    }

    override fun findById(id: String): User? {
        // A malformed id can never match a uuid column, and must not surface as a 500.
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        return dataSource.connection.use { c ->
            c.prepareStatement("$SELECT WHERE id = ? AND $ACTIVE").use { ps ->
                ps.setObject(1, uuid)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
            }
        }
    }

    override fun create(email: String, displayName: String, phone: String, passwordHash: String): CreateUserResult {
        repeat(MAX_REFERRAL_ATTEMPTS) {
            try {
                return insert(email, displayName, phone, passwordHash)
                    ?.let { CreateUserResult.Created(it) }
                    ?: CreateUserResult.PhoneTaken
            } catch (e: SQLException) {
                if (e.sqlState != UNIQUE_VIOLATION) throw e
                when ((e as? PSQLException)?.serverErrorMessage?.constraint) {
                    EMAIL_INDEX -> return CreateUserResult.EmailTaken

                    // Generated code collided with an existing one (10 random chars): draw another.
                    REFERRAL_INDEX -> Unit

                    else -> throw e
                }
            }
        }
        error("Could not allocate a unique referral code after $MAX_REFERRAL_ATTEMPTS attempts")
    }

    override fun recordLogin(id: String) {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return
        dataSource.connection.use { c ->
            c.prepareStatement("UPDATE users SET last_login_at = now() WHERE id = ?").use { ps ->
                ps.setObject(1, uuid)
                ps.executeUpdate()
            }
        }
    }

    override fun verifiedPhoneOwner(phone: String): String? = dataSource.connection.use { c ->
        c.prepareStatement(
            "SELECT id FROM users WHERE phone = ? AND phone_verified_at IS NOT NULL AND deleted_at IS NULL",
        ).use { ps ->
            ps.setString(1, phone)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getObject(1, UUID::class.java).toString() else null }
        }
    }

    override fun markPhoneVerified(id: String, phone: String): PhoneVerifiedResult {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return PhoneVerifiedResult.PHONE_CHANGED
        return try {
            dataSource.connection.use { c ->
                c.prepareStatement(
                    """UPDATE users SET phone_verified_at = coalesce(phone_verified_at, now())
                       WHERE id = ? AND phone = ? AND $ACTIVE""",
                ).use { ps ->
                    ps.setObject(1, uuid)
                    ps.setString(2, phone)
                    if (ps.executeUpdate() > 0) PhoneVerifiedResult.VERIFIED else PhoneVerifiedResult.PHONE_CHANGED
                }
            }
        } catch (e: SQLException) {
            // users_phone_verified_uq: another account verified this number first. The index, not
            // application code, is what makes "first verifier wins" hold under concurrency.
            if (e.sqlState == UNIQUE_VIOLATION &&
                (e as? PSQLException)?.serverErrorMessage?.constraint == PHONE_VERIFIED_INDEX
            ) {
                PhoneVerifiedResult.CLAIMED_BY_ANOTHER
            } else {
                throw e
            }
        }
    }

    /** @return null when a verified account already owns [phone] (no row inserted). */
    private fun insert(email: String, displayName: String, phone: String, passwordHash: String): User? = dataSource.connection.use { c ->
        c.prepareStatement(
            """INSERT INTO users (email, full_name, phone, password_hash, referral_code)
                   SELECT ?, ?, ?, ?, ?
                   WHERE NOT EXISTS (
                       SELECT 1 FROM users
                       WHERE phone = ? AND phone_verified_at IS NOT NULL AND deleted_at IS NULL)
                   RETURNING $COLUMNS""",
        ).use { ps ->
            ps.setString(1, email)
            ps.setString(2, displayName)
            ps.setString(3, phone)
            ps.setString(4, passwordHash)
            ps.setString(5, newReferralCode())
            ps.setString(6, phone)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
    }

    private fun ResultSet.toUser() = User(
        id = getObject("id", UUID::class.java).toString(),
        email = getString("email"),
        displayName = getString("full_name"),
        phone = getString("phone"),
        phoneVerified = getBoolean("phone_verified"),
        passwordHash = getString("password_hash"),
    )

    private companion object {
        const val COLUMNS = "id, email, full_name, phone, (phone_verified_at IS NOT NULL) AS phone_verified, password_hash"
        const val SELECT = "SELECT $COLUMNS FROM users"
        const val ACTIVE = "deleted_at IS NULL AND status = 'active'"

        const val UNIQUE_VIOLATION = "23505"
        const val EMAIL_INDEX = "users_email_active_uq"
        const val PHONE_VERIFIED_INDEX = "users_phone_verified_uq"
        const val REFERRAL_INDEX = "users_referral_code_uq"
        const val MAX_REFERRAL_ATTEMPTS = 5
    }
}

// No 0/O/1/I/L: codes get read aloud and typed by friends.
private const val REFERRAL_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
private val referralRandom = SecureRandom()

fun randomReferralCode(length: Int = 10): String =
    buildString(length) { repeat(length) { append(REFERRAL_ALPHABET[referralRandom.nextInt(REFERRAL_ALPHABET.length)]) } }
