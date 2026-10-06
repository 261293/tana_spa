package com.tana.spa.auth.otp

import java.time.Duration

/**
 * Anti-abuse policy for one-time codes. SMS costs real money per message, and attackers abuse OTP
 * endpoints to generate traffic to premium-rate numbers ("SMS pumping"), so every limit here is a
 * cost control as much as a security control.
 *
 * Environment variables (all optional):
 *  - `OTP_ALLOWED_COUNTRIES` comma separated ISO codes, e.g. `VN,US`. Empty/unset = every country.
 *  - `OTP_BLOCKED_COUNTRIES` ISO codes to refuse even when everything else is allowed.
 *  - `DEFAULT_PHONE_REGION`  region assumed for numbers typed without `+countrycode` (default `VN`).
 */
data class OtpConfig(
    /** How long a code is accepted. Keep <= the provider's own expiry (Twilio Verify: 10 minutes). */
    val codeTtl: Duration = Duration.ofMinutes(10),
    val resendCooldown: Duration = Duration.ofSeconds(60),
    val maxSendsPerPhonePerHour: Int = 5,
    val maxSendsPerUserPerDay: Int = 10,
    val maxSendsPerIpPerHour: Int = 20,
    /** Guesses allowed per code. With 6 digits, 5 tries is a 1-in-200,000 shot. */
    val maxCheckAttempts: Int = 5,
    val allowedCountries: Set<String> = emptySet(),
    val blockedCountries: Set<String> = emptySet(),
    val defaultPhoneRegion: String = "VN",
) {
    init {
        require(codeTtl.seconds in 30..3600) { "codeTtl must be between 30s and 1h" }
        require(maxCheckAttempts in 1..10) { "maxCheckAttempts must be between 1 and 10" }
    }

    fun allows(region: String): Boolean =
        region !in blockedCountries && (allowedCountries.isEmpty() || region in allowedCountries)

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): OtpConfig {
            fun countries(name: String) = env[name].orEmpty().split(',')
                .map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                .onEach { require(it.matches(Regex("^[A-Z]{2}$"))) { "$name: '$it' is not an ISO 3166-1 alpha-2 code" } }
                .toSet()
            return OtpConfig(
                allowedCountries = countries("OTP_ALLOWED_COUNTRIES"),
                blockedCountries = countries("OTP_BLOCKED_COUNTRIES"),
                defaultPhoneRegion = env["DEFAULT_PHONE_REGION"]?.uppercase() ?: "VN",
            )
        }
    }
}
