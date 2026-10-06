package com.tana.spa.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

interface PasswordHasher {
    fun hash(password: String): String
    fun verify(password: String, stored: String): Boolean
}

/**
 * PBKDF2-HMAC-SHA256 with a per-password random salt (OWASP-recommended iteration count).
 * Stored form: `pbkdf2$<iterations>$<salt b64>$<hash b64>`, so the cost can be raised later
 * while old hashes keep verifying.
 */
class Pbkdf2PasswordHasher(private val iterations: Int = 600_000) : PasswordHasher {
    private val random = SecureRandom()

    override fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return encode(iterations, salt, derive(password, salt, iterations))
    }

    override fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iter = parts[1].toIntOrNull() ?: return false
        val decoder = Base64.getDecoder()
        return try {
            val salt = decoder.decode(parts[2])
            val expected = decoder.decode(parts[3])
            // Constant-time comparison: no early exit that leaks how many bytes matched.
            MessageDigest.isEqual(expected, derive(password, salt, iter))
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun derive(password: String, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iter, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(iter: Int, salt: ByteArray, hash: ByteArray): String {
        val e = Base64.getEncoder()
        return listOf("pbkdf2", iter.toString(), e.encodeToString(salt), e.encodeToString(hash)).joinToString("$")
    }

    private companion object {
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
    }
}
