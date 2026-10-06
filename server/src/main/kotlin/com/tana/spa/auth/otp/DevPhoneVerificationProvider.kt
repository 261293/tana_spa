package com.tana.spa.auth.otp

import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline stand-in for a real SMS provider: generates a genuine random 6-digit code, keeps it in
 * memory with an expiry, and writes it to the log instead of sending it. Lets the whole flow be
 * exercised locally and in tests without credentials or cost. Never used in production
 * (see [PhoneVerificationProvider.fromEnvironment]).
 */
class DevPhoneVerificationProvider(
    private val ttl: Duration = Duration.ofMinutes(10),
    private val clock: () -> Instant = Instant::now,
) : PhoneVerificationProvider {

    override val name = "dev"

    private class Entry(val code: String, val expiresAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val random = SecureRandom()

    override fun start(phoneE164: String, locale: String?): String? {
        val code = (0 until CODE_LENGTH).joinToString("") { random.nextInt(10).toString() }
        entries[phoneE164] = Entry(code, clock() + ttl)
        log.warn("DEV OTP for {}: {}", phoneE164, code)
        return null
    }

    override fun check(phoneE164: String, code: String): Boolean {
        val entry = entries[phoneE164] ?: return false
        if (!clock().isBefore(entry.expiresAt)) {
            entries.remove(phoneE164, entry)
            return false
        }
        // Constant-time compare, as a real verifier would.
        val match = MessageDigest.isEqual(entry.code.toByteArray(), code.toByteArray())
        // Single use: an approved code cannot be replayed.
        if (match) entries.remove(phoneE164, entry)
        return match
    }

    /** Test hook: the code most recently "sent" to [phoneE164]. */
    fun peekCode(phoneE164: String): String? = entries[phoneE164]?.code

    private companion object {
        val log = LoggerFactory.getLogger(DevPhoneVerificationProvider::class.java)
        const val CODE_LENGTH = 6
    }
}
