package com.tana.spa.auth.otp

import java.time.Instant
import java.util.UUID

enum class VerificationStatus(val sql: String) {
    PENDING("pending"),
    APPROVED("approved"),
    CANCELED("canceled"),
    EXPIRED("expired"),
    FAILED("failed"),
    ;

    companion object {
        fun fromSql(value: String) = entries.first { it.sql == value }
    }
}

data class PhoneVerification(
    val id: String,
    val userId: String,
    val phone: String,
    val region: String,
    val provider: String,
    val status: VerificationStatus,
    val attempts: Int,
    val sentAt: Instant,
    val expiresAt: Instant,
)

data class SendStats(val lastSentAt: Instant?, val phoneSendsInWindow: Int, val userSendsInDay: Int, val ipSendsInWindow: Int)

/**
 * Bookkeeping for sent codes (the codes themselves are never stored). Backs rate limits, the
 * per-code guess cap and fraud monitoring. Blocking, like [com.tana.spa.auth.UserRepository].
 */
interface PhoneVerificationRepository {
    /** Send counts used to enforce cooldown and hourly/daily caps. */
    fun sendStats(userId: String, phone: String, ip: String?, hourAgo: Instant, dayAgo: Instant): SendStats

    /**
     * Cancels the user's previous live code and records a new one, atomically.
     * @return null if a concurrent request created one first (the caller should treat it as rate limited).
     */
    fun createPending(
        userId: String,
        phone: String,
        region: String,
        provider: String,
        ip: String?,
        sentAt: Instant,
        expiresAt: Instant,
    ): PhoneVerification?

    fun findPending(userId: String): PhoneVerification?
    fun setProviderRef(id: String, ref: String?)
    fun markFailed(id: String)
    fun markApproved(id: String, at: Instant)
    fun markExpired(id: String)
    fun cancel(id: String)

    /**
     * Atomically spends one guess. Done BEFORE checking the code so that a burst of parallel
     * guesses can never exceed [max] checks against the provider.
     * @return the number of guesses spent so far (including this one), or null if none are left
     *   or the code is no longer pending.
     */
    fun consumeAttempt(id: String, max: Int): Int?
}

class InMemoryPhoneVerificationRepository : PhoneVerificationRepository {
    private class Row(
        val id: String,
        val userId: String,
        val phone: String,
        val region: String,
        val provider: String,
        val ip: String?,
        val sentAt: Instant,
        val expiresAt: Instant,
        var status: VerificationStatus = VerificationStatus.PENDING,
        var attempts: Int = 0,
    ) {
        fun toModel() = PhoneVerification(id, userId, phone, region, provider, status, attempts, sentAt, expiresAt)
    }

    private val rows = mutableListOf<Row>()

    @Synchronized
    override fun sendStats(userId: String, phone: String, ip: String?, hourAgo: Instant, dayAgo: Instant) = SendStats(
        lastSentAt = rows.filter { it.userId == userId }.maxOfOrNull { it.sentAt },
        phoneSendsInWindow = rows.count { it.phone == phone && it.sentAt >= hourAgo },
        userSendsInDay = rows.count { it.userId == userId && it.sentAt >= dayAgo },
        ipSendsInWindow = if (ip == null) 0 else rows.count { it.ip == ip && it.sentAt >= hourAgo },
    )

    @Synchronized
    override fun createPending(
        userId: String,
        phone: String,
        region: String,
        provider: String,
        ip: String?,
        sentAt: Instant,
        expiresAt: Instant,
    ): PhoneVerification {
        rows.filter { it.userId == userId && it.status == VerificationStatus.PENDING }
            .forEach { it.status = VerificationStatus.CANCELED }
        val row = Row(UUID.randomUUID().toString(), userId, phone, region, provider, ip, sentAt, expiresAt)
        rows += row
        return row.toModel()
    }

    @Synchronized
    override fun findPending(userId: String): PhoneVerification? =
        rows.lastOrNull { it.userId == userId && it.status == VerificationStatus.PENDING }?.toModel()

    @Synchronized override fun setProviderRef(id: String, ref: String?) = Unit

    @Synchronized override fun markFailed(id: String) = set(id, VerificationStatus.FAILED)

    @Synchronized override fun markApproved(id: String, at: Instant) = set(id, VerificationStatus.APPROVED)

    @Synchronized override fun markExpired(id: String) = set(id, VerificationStatus.EXPIRED)

    @Synchronized override fun cancel(id: String) = set(id, VerificationStatus.CANCELED)

    @Synchronized
    override fun consumeAttempt(id: String, max: Int): Int? {
        val row = rows.firstOrNull { it.id == id } ?: return null
        if (row.status != VerificationStatus.PENDING || row.attempts >= max) return null
        return ++row.attempts
    }

    private fun set(id: String, status: VerificationStatus) {
        rows.firstOrNull { it.id == id && it.status == VerificationStatus.PENDING }?.status = status
    }
}
