package com.tana.spa.auth.otp

import com.tana.spa.auth.PhoneNumbers
import com.tana.spa.auth.PhoneVerifiedResult
import com.tana.spa.auth.User
import com.tana.spa.auth.UserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

sealed interface SendResult {
    data class Sent(val resendAfterSeconds: Int, val expiresInSeconds: Int, val maskedPhone: String) : SendResult
    data object UserNotFound : SendResult
    data object NoPhone : SendResult
    data object AlreadyVerified : SendResult

    /** A different account already proved ownership of this number. */
    data object PhoneClaimed : SendResult
    data class CountryNotSupported(val region: String) : SendResult

    /** [retryAfterSeconds] is an upper bound, not an exact time. */
    data class RateLimited(val retryAfterSeconds: Int) : SendResult
    data object ProviderUnavailable : SendResult
}

sealed interface VerifyResult {
    data class Verified(val user: User) : VerifyResult
    data object NotRequested : VerifyResult
    data object Expired : VerifyResult
    data class InvalidCode(val attemptsRemaining: Int) : VerifyResult
    data object TooManyAttempts : VerifyResult
    data object PhoneClaimed : VerifyResult
    data object ProviderUnavailable : VerifyResult
}

/**
 * Phone-number verification with one-time codes.
 *
 * Abuse resistance, in layers (each guards a different attack):
 *  1. **The code only ever goes to the number already on the account.** The client cannot name a
 *     destination, which removes the classic SMS-pumping vector (hitting an endpoint with
 *     attacker-chosen premium-rate numbers). An attacker first needs a registered account.
 *  2. Per-user cooldown, per-number hourly cap, per-user daily cap, per-IP hourly cap.
 *  3. Optional country allow/block list ([OtpConfig]).
 *  4. A hard cap on guesses per code, spent atomically before each check.
 *  5. The provider's own fraud controls (Twilio Fraud Guard).
 *
 * All blocking work (JDBC, HTTP to the provider) runs on [io].
 */
class PhoneVerificationService(
    private val users: UserRepository,
    private val verifications: PhoneVerificationRepository,
    private val provider: PhoneVerificationProvider,
    private val config: OtpConfig = OtpConfig(),
    private val clock: () -> Instant = Instant::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun send(userId: String, ip: String?, locale: String?): SendResult = withContext(io) {
        val user = users.findById(userId) ?: return@withContext SendResult.UserNotFound
        val phone = user.phone ?: return@withContext SendResult.NoPhone
        if (user.phoneVerified) return@withContext SendResult.AlreadyVerified

        // Stored numbers are already E.164; parsing again recovers the region and re-checks validity.
        val parsed = PhoneNumbers.parse(phone, config.defaultPhoneRegion) ?: return@withContext SendResult.NoPhone
        if (!config.allows(parsed.region)) return@withContext SendResult.CountryNotSupported(parsed.region)

        val owner = users.verifiedPhoneOwner(phone)
        if (owner != null && owner != userId) return@withContext SendResult.PhoneClaimed

        val now = clock()
        val cleanIp = ip?.takeIf { IP_PATTERN.matches(it) }
        val stats = verifications.sendStats(userId, phone, cleanIp, now - HOUR, now - DAY)

        stats.lastSentAt?.let { last ->
            val wait = Duration.between(now, last + config.resendCooldown)
            if (!wait.isNegative && !wait.isZero) {
                log.info("OTP send refused for user {}: resend cooldown, {}s left", userId, ceilSeconds(wait))
                return@withContext SendResult.RateLimited(ceilSeconds(wait))
            }
        }
        if (stats.phoneSendsInWindow >= config.maxSendsPerPhonePerHour ||
            stats.ipSendsInWindow >= config.maxSendsPerIpPerHour
        ) {
            // Worth a WARN: this is the pattern abuse (SMS pumping) produces.
            log.warn(
                "OTP send refused for user {}: hourly cap (phone={}, ip={}) region={}",
                userId,
                stats.phoneSendsInWindow,
                stats.ipSendsInWindow,
                parsed.region,
            )
            return@withContext SendResult.RateLimited(HOUR.seconds.toInt())
        }
        if (stats.userSendsInDay >= config.maxSendsPerUserPerDay) {
            log.warn("OTP send refused for user {}: daily cap ({} sends)", userId, stats.userSendsInDay)
            return@withContext SendResult.RateLimited(DAY.seconds.toInt())
        }

        // Recorded BEFORE calling the provider: a failed or slow send still counts against the
        // limits, so an outage cannot be turned into unlimited retries.
        val pending = verifications.createPending(
            userId = userId,
            phone = phone,
            region = parsed.region,
            provider = provider.name,
            ip = cleanIp,
            sentAt = now,
            expiresAt = now + config.codeTtl,
        ) ?: return@withContext SendResult.RateLimited(config.resendCooldown.seconds.toInt())

        try {
            verifications.setProviderRef(pending.id, provider.start(phone, locale?.takeIf { LOCALE_PATTERN.matches(it) }))
        } catch (e: ProviderException) {
            log.error("OTP send failed for user {} via {} region {}: {}", userId, provider.name, parsed.region, e.message)
            verifications.markFailed(pending.id)
            return@withContext SendResult.ProviderUnavailable
        }

        log.info("OTP sent for user {} via {} region {}", userId, provider.name, parsed.region)
        SendResult.Sent(
            resendAfterSeconds = config.resendCooldown.seconds.toInt(),
            expiresInSeconds = config.codeTtl.seconds.toInt(),
            maskedPhone = PhoneNumbers.mask(phone),
        )
    }

    suspend fun verify(userId: String, code: String): VerifyResult = withContext(io) {
        val pending = verifications.findPending(userId) ?: return@withContext VerifyResult.NotRequested
        if (!clock().isBefore(pending.expiresAt)) {
            verifications.markExpired(pending.id)
            return@withContext VerifyResult.Expired
        }

        val trimmed = code.trim()
        // A malformed code is a typo, not a guess: reject it without spending an attempt.
        if (!CODE_PATTERN.matches(trimmed)) {
            return@withContext VerifyResult.InvalidCode(maxOf(config.maxCheckAttempts - pending.attempts, 0))
        }

        val spent = verifications.consumeAttempt(pending.id, config.maxCheckAttempts)
        if (spent == null) {
            log.warn("OTP verify for user {}: attempt limit reached, code cancelled", userId)
            verifications.cancel(pending.id)
            return@withContext VerifyResult.TooManyAttempts
        }

        val approved = try {
            provider.check(pending.phone, trimmed)
        } catch (e: ProviderException) {
            log.error("OTP check failed for user {} via {}: {}", userId, provider.name, e.message)
            return@withContext VerifyResult.ProviderUnavailable
        }

        if (!approved) {
            val remaining = config.maxCheckAttempts - spent
            if (remaining <= 0) {
                verifications.cancel(pending.id)
                return@withContext VerifyResult.TooManyAttempts
            }
            return@withContext VerifyResult.InvalidCode(remaining)
        }

        when (users.markPhoneVerified(userId, pending.phone)) {
            PhoneVerifiedResult.VERIFIED -> {
                log.info("Phone verified for user {}", userId)
                verifications.markApproved(pending.id, clock())
                val user = users.findById(userId) ?: return@withContext VerifyResult.NotRequested
                VerifyResult.Verified(user)
            }

            PhoneVerifiedResult.PHONE_CHANGED -> {
                log.warn("OTP approved for user {} but the account phone changed meanwhile", userId)
                verifications.cancel(pending.id)
                VerifyResult.NotRequested
            }

            PhoneVerifiedResult.CLAIMED_BY_ANOTHER -> {
                log.warn("OTP approved for user {} but another account already verified that phone", userId)
                verifications.cancel(pending.id)
                VerifyResult.PhoneClaimed
            }
        }
    }

    private fun ceilSeconds(d: Duration): Int = (d.seconds + if (d.nano > 0) 1 else 0).toInt()

    private companion object {
        val log = LoggerFactory.getLogger(PhoneVerificationService::class.java)
        val HOUR: Duration = Duration.ofHours(1)
        val DAY: Duration = Duration.ofDays(1)
        val CODE_PATTERN = Regex("^[0-9]{4,10}$")
        val LOCALE_PATTERN = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})?$")

        // IPv4 or IPv6 literal characters only; anything else (hostnames, junk) is treated as "unknown".
        val IP_PATTERN = Regex("^([0-9]{1,3}(\\.[0-9]{1,3}){3}|[0-9A-Fa-f:.]{2,45})$")
    }
}
