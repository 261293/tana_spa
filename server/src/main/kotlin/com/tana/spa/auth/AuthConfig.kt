package com.tana.spa.auth

import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64

/**
 * Runtime configuration, read from the environment so no secret lives in source control.
 *
 *  - `JWT_SECRET`            HS256 signing key (>= 32 chars). Random per-process when unset,
 *                            which is fine for local dev but logs every user out on restart.
 *  - `CORS_ALLOWED_ORIGINS`  comma separated origins (`https://app.example.com`) for the web client.
 *                            Defaults to the local wasm dev server.
 */
data class AuthConfig(
    val jwtSecret: String,
    val issuer: String = "tana-spa",
    val audience: String = "tana-spa-app",
    val tokenTtlSeconds: Long = 60 * 60,
    val allowedOrigins: List<String> = DEFAULT_ORIGINS,
    val loginRateLimitPerMinute: Int = 10,
    /**
     * Set `TRUST_PROXY=true` ONLY behind a reverse proxy/load balancer that sets X-Forwarded-For.
     * Without it every client appears to come from the proxy, so per-client rate limits would
     * throttle everyone together; with it on a directly exposed server, clients could forge the header.
     */
    val trustProxy: Boolean = false,
) {
    init {
        require(jwtSecret.length >= 32) { "JWT_SECRET must be at least 32 characters" }
    }

    companion object {
        private val DEFAULT_ORIGINS = listOf("http://localhost:8080")
        private val log = LoggerFactory.getLogger(AuthConfig::class.java)

        fun fromEnvironment(): AuthConfig {
            val secret = System.getenv("JWT_SECRET") ?: run {
                // A random per-process secret would silently log everyone out on every deploy and
                // break any second instance, so production must configure one explicitly.
                check(System.getenv("APP_ENV") != "production") { "JWT_SECRET must be set when APP_ENV=production" }
                log.warn("JWT_SECRET is not set; using a random secret. Tokens will not survive a restart.")
                randomSecret()
            }
            val origins = System.getenv("CORS_ALLOWED_ORIGINS")
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            return AuthConfig(
                jwtSecret = secret,
                allowedOrigins = origins ?: DEFAULT_ORIGINS,
                trustProxy = System.getenv("TRUST_PROXY").equals("true", ignoreCase = true),
            )
        }

        private fun randomSecret(): String {
            val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
