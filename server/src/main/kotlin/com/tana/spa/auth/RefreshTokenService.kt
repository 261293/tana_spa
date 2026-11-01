package com.tana.spa.auth

import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

data class IssuedRefreshToken(val token: String, val expiresAtMillis: Long)

sealed interface RotateOutcome {
    data class Rotated(val user: User, val refresh: IssuedRefreshToken) : RotateOutcome

    /** Unknown, expired, revoked, or the account is gone. */
    data object Rejected : RotateOutcome

    /**
     * A token that had already been spent was presented again. Either it was stolen and replayed,
     * or the holder kept a copy; both mean the chain cannot be trusted, so the whole family is
     * revoked and every device descended from that sign-in has to sign in again.
     */
    data object Reused : RotateOutcome
}

/**
 * Rotating refresh tokens (`refresh_tokens`, V1). Each one may be spent exactly once and its
 * replacement continues the same family, so a stolen token is usable only until the real client
 * next refreshes — at which point one of the two presents a spent token and the family dies.
 */
class RefreshTokenService(
    private val tokens: RefreshTokenRepository,
    private val users: UserRepository,
    private val ttlMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newToken: () -> String = ::randomRefreshToken,
) {

    fun issue(userId: String): IssuedRefreshToken = store(userId, UUID.randomUUID().toString()).issued

    fun rotate(rawToken: String): RotateOutcome {
        val stored = tokens.find(hash(rawToken)) ?: return RotateOutcome.Rejected
        if (stored.replaced) {
            log.warn("refresh token reuse detected for user {}; revoking family", stored.userId)
            tokens.revokeFamily(stored.familyId)
            return RotateOutcome.Reused
        }
        if (stored.revoked || stored.expiresAtMillis <= nowMillis()) return RotateOutcome.Rejected
        val user = users.findById(stored.userId) ?: return RotateOutcome.Rejected

        val replacement = store(stored.userId, stored.familyId)
        tokens.markReplaced(stored.id, replacement.id)
        return RotateOutcome.Rotated(user, replacement.issued)
    }

    /** Signing out: the whole family goes, not just the token presented. */
    fun revoke(rawToken: String) {
        tokens.find(hash(rawToken))?.let { tokens.revokeFamily(it.familyId) }
    }

    fun deleteExpired(): Int = tokens.deleteExpiredBefore(nowMillis())

    private fun store(userId: String, familyId: String): StoredIssue {
        val raw = newToken()
        val expiresAt = nowMillis() + ttlMillis
        val id = tokens.insert(userId, familyId, hash(raw), expiresAt)
        return StoredIssue(id, IssuedRefreshToken(raw, expiresAt))
    }

    private data class StoredIssue(val id: String, val issued: IssuedRefreshToken)

    private companion object {
        val log: org.slf4j.Logger = LoggerFactory.getLogger(RefreshTokenService::class.java)
    }
}

/** SHA-256, which is what the table's `octet_length(token_hash) = 32` check expects. */
fun hash(rawToken: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())

private const val TOKEN_BYTES = 32

private fun randomRefreshToken(): String {
    val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
