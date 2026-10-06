package com.tana.spa.auth.otp

import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

/** PostgreSQL-backed [PhoneVerificationRepository] (schema: `V5__phone_verification.sql`). */
class PostgresPhoneVerificationRepository(private val dataSource: DataSource) : PhoneVerificationRepository {

    override fun sendStats(userId: String, phone: String, ip: String?, hourAgo: Instant, dayAgo: Instant): SendStats {
        val user = uuid(userId) ?: return SendStats(null, 0, 0, 0)
        return dataSource.connection.use { c ->
            c.prepareStatement(
                """SELECT
                     (SELECT max(sent_at) FROM phone_verifications WHERE user_id = ?) AS last_sent,
                     (SELECT count(*) FROM phone_verifications WHERE phone = ? AND sent_at >= ?) AS phone_sends,
                     (SELECT count(*) FROM phone_verifications WHERE user_id = ? AND sent_at >= ?) AS user_sends,
                     (SELECT count(*) FROM phone_verifications WHERE ip = ? AND sent_at >= ?) AS ip_sends""",
            ).use { ps ->
                ps.setObject(1, user)
                ps.setString(2, phone)
                ps.setObject(3, odt(hourAgo))
                ps.setObject(4, user)
                ps.setObject(5, odt(dayAgo))
                // `ip = NULL` is never true, so an unknown address simply counts as zero sends.
                if (ip == null) ps.setNull(6, Types.VARCHAR) else ps.setString(6, ip)
                ps.setObject(7, odt(hourAgo))
                ps.executeQuery().use { rs ->
                    rs.next()
                    SendStats(
                        lastSentAt = rs.getObject("last_sent", OffsetDateTime::class.java)?.toInstant(),
                        phoneSendsInWindow = rs.getInt("phone_sends"),
                        userSendsInDay = rs.getInt("user_sends"),
                        ipSendsInWindow = rs.getInt("ip_sends"),
                    )
                }
            }
        }
    }

    override fun createPending(
        userId: String, phone: String, region: String, provider: String, ip: String?,
        sentAt: Instant, expiresAt: Instant,
    ): PhoneVerification? {
        val user = uuid(userId) ?: return null
        return dataSource.connection.use { c ->
            c.autoCommit = false
            try {
                c.prepareStatement(
                    "UPDATE phone_verifications SET status = 'canceled' WHERE user_id = ? AND status = 'pending'",
                ).use { ps -> ps.setObject(1, user); ps.executeUpdate() }

                val created = c.prepareStatement(
                    """INSERT INTO phone_verifications (user_id, phone, country_code, provider, ip, sent_at, expires_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?)
                       RETURNING $COLUMNS""",
                ).use { ps ->
                    ps.setObject(1, user)
                    ps.setString(2, phone)
                    ps.setString(3, region)
                    ps.setString(4, provider)
                    if (ip == null) ps.setNull(5, Types.VARCHAR) else ps.setString(5, ip)
                    ps.setObject(6, odt(sentAt))
                    ps.setObject(7, odt(expiresAt))
                    ps.executeQuery().use { rs -> rs.next(); rs.toModel() }
                }
                c.commit()
                created
            } catch (e: SQLException) {
                c.rollback()
                // phone_verifications_one_pending_uq: a parallel request won the race.
                if (e.sqlState == UNIQUE_VIOLATION &&
                    (e as? PSQLException)?.serverErrorMessage?.constraint == ONE_PENDING_INDEX
                ) {
                    null
                } else {
                    throw e
                }
            }
        }
    }

    override fun findPending(userId: String): PhoneVerification? {
        val user = uuid(userId) ?: return null
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT $COLUMNS FROM phone_verifications WHERE user_id = ? AND status = 'pending'").use { ps ->
                ps.setObject(1, user)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toModel() else null }
            }
        }
    }

    override fun setProviderRef(id: String, ref: String?) {
        val row = uuid(id) ?: return
        dataSource.connection.use { c ->
            c.prepareStatement("UPDATE phone_verifications SET provider_ref = ? WHERE id = ?").use { ps ->
                if (ref == null) ps.setNull(1, Types.VARCHAR) else ps.setString(1, ref)
                ps.setObject(2, row)
                ps.executeUpdate()
            }
        }
    }

    override fun markFailed(id: String) = transition(id, "failed")
    override fun markExpired(id: String) = transition(id, "expired")
    override fun cancel(id: String) = transition(id, "canceled")

    override fun markApproved(id: String, at: Instant) {
        val row = uuid(id) ?: return
        dataSource.connection.use { c ->
            c.prepareStatement(
                "UPDATE phone_verifications SET status = 'approved', verified_at = ? WHERE id = ? AND status = 'pending'",
            ).use { ps -> ps.setObject(1, odt(at)); ps.setObject(2, row); ps.executeUpdate() }
        }
    }

    override fun consumeAttempt(id: String, max: Int): Int? {
        val row = uuid(id) ?: return null
        return dataSource.connection.use { c ->
            // One atomic statement: concurrent guesses are serialised by the row lock, and the
            // `attempts < ?` guard makes it impossible to spend more than `max`.
            c.prepareStatement(
                """UPDATE phone_verifications SET attempts = attempts + 1
                   WHERE id = ? AND status = 'pending' AND attempts < ?
                   RETURNING attempts""",
            ).use { ps ->
                ps.setObject(1, row)
                ps.setInt(2, max)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
            }
        }
    }

    private fun transition(id: String, status: String) {
        val row = uuid(id) ?: return
        dataSource.connection.use { c: Connection ->
            c.prepareStatement("UPDATE phone_verifications SET status = ? WHERE id = ? AND status = 'pending'").use { ps ->
                ps.setString(1, status)
                ps.setObject(2, row)
                ps.executeUpdate()
            }
        }
    }

    private fun ResultSet.toModel() = PhoneVerification(
        id = getObject("id", UUID::class.java).toString(),
        userId = getObject("user_id", UUID::class.java).toString(),
        phone = getString("phone"),
        region = getString("country_code"),
        provider = getString("provider"),
        status = VerificationStatus.fromSql(getString("status")),
        attempts = getInt("attempts"),
        sentAt = getObject("sent_at", OffsetDateTime::class.java).toInstant(),
        expiresAt = getObject("expires_at", OffsetDateTime::class.java).toInstant(),
    )

    private fun uuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
    private fun odt(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)

    private companion object {
        const val COLUMNS = "id, user_id, phone, country_code, provider, status, attempts, sent_at, expires_at"
        const val UNIQUE_VIOLATION = "23505"
        const val ONE_PENDING_INDEX = "phone_verifications_one_pending_uq"
    }
}
