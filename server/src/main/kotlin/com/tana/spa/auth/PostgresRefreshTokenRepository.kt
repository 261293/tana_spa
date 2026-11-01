package com.tana.spa.auth

import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-backed [RefreshTokenRepository] (schema: `V1`).
 *
 * Rows are looked up by `token_hash`, which the table makes unique, so the raw token never has to
 * be stored or compared.
 */
class PostgresRefreshTokenRepository(private val dataSource: DataSource) : RefreshTokenRepository {

    override fun insert(userId: String, familyId: String, tokenHash: ByteArray, expiresAtMillis: Long): String =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """.trimIndent(),
            ).use { ps ->
                ps.setObject(1, UUID.fromString(userId))
                ps.setObject(2, UUID.fromString(familyId))
                ps.setBytes(3, tokenHash)
                ps.setObject(4, expiresAtMillis.toTimestamp())
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getObject("id", UUID::class.java).toString()
                }
            }
        }

    override fun find(tokenHash: ByteArray): StoredRefreshToken? = dataSource.connection.use { c ->
        c.prepareStatement(
            """
            SELECT id, user_id, family_id, expires_at, revoked_at, replaced_by
            FROM refresh_tokens
            WHERE token_hash = ?
            """.trimIndent(),
        ).use { ps ->
            ps.setBytes(1, tokenHash)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toStoredRefreshToken() else null }
        }
    }

    override fun markReplaced(id: String, replacedById: String) {
        dataSource.connection.use { c ->
            c.prepareStatement("UPDATE refresh_tokens SET replaced_by = ? WHERE id = ?").use { ps ->
                ps.setObject(1, UUID.fromString(replacedById))
                ps.setObject(2, UUID.fromString(id))
                ps.executeUpdate()
            }
        }
    }

    override fun revokeFamily(familyId: String) {
        dataSource.connection.use { c ->
            c.prepareStatement("UPDATE refresh_tokens SET revoked_at = now() WHERE family_id = ? AND revoked_at IS NULL").use { ps ->
                ps.setObject(1, UUID.fromString(familyId))
                ps.executeUpdate()
            }
        }
    }

    override fun deleteExpiredBefore(millis: Long): Int = dataSource.connection.use { c ->
        c.prepareStatement("DELETE FROM refresh_tokens WHERE expires_at < ?").use { ps ->
            ps.setObject(1, millis.toTimestamp())
            ps.executeUpdate()
        }
    }
}

private fun Long.toTimestamp(): OffsetDateTime = Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC)

private fun ResultSet.toStoredRefreshToken() = StoredRefreshToken(
    id = getObject("id", UUID::class.java).toString(),
    userId = getObject("user_id", UUID::class.java).toString(),
    familyId = getObject("family_id", UUID::class.java).toString(),
    expiresAtMillis = getObject("expires_at", OffsetDateTime::class.java).toInstant().toEpochMilli(),
    revoked = getObject("revoked_at", OffsetDateTime::class.java) != null,
    replaced = getObject("replaced_by", UUID::class.java) != null,
)
