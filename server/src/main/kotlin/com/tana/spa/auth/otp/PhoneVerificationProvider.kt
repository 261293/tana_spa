package com.tana.spa.auth.otp

import org.slf4j.LoggerFactory

/**
 * Delivers and checks one-time codes. The rest of the server only knows this interface, so the
 * SMS vendor (Twilio Verify, Vonage, a local Vietnamese gateway, ...) can be swapped without
 * touching routes, rate limits or the database.
 */
interface PhoneVerificationProvider {
    /** Stored on each verification for auditing, e.g. `twilio_verify`. */
    val name: String

    /**
     * Sends a fresh code to [phoneE164] (a number already validated and normalised).
     * @param locale BCP-47 language for the message ("vi", "en", "pt-BR"), or null for the provider default.
     * @return the provider's reference for this verification, if it has one.
     * @throws ProviderException when the code could not be sent.
     */
    fun start(phoneE164: String, locale: String?): String?

    /**
     * @return true only when [code] is the live code for [phoneE164]. A wrong, expired or
     *   already-used code is `false`, not an exception.
     * @throws ProviderException when the provider could not be reached or answered with an error.
     */
    fun check(phoneE164: String, code: String): Boolean

    companion object {
        private val log = LoggerFactory.getLogger(PhoneVerificationProvider::class.java)

        /**
         * Twilio Verify when its three variables are set; otherwise the offline dev provider,
         * which is refused in production because it would "send" codes into the server log.
         *
         *  - `TWILIO_VERIFY_SERVICE_SID`  (`VA...`)
         *  - `TWILIO_API_KEY_SID`         (`SK...`)  scoped API key, not the account auth token
         *  - `TWILIO_API_KEY_SECRET`
         */
        fun fromEnvironment(env: Map<String, String> = System.getenv()): PhoneVerificationProvider {
            val appEnv = env["APP_ENV"]?.trim()?.lowercase()
            // Dev/SIT: no real verification (and no SMS cost), even if Twilio happens to be configured.
            if (appEnv in SKIP_VERIFICATION_ENVS) {
                log.warn("APP_ENV={}: phone verification is skipped, ANY code is accepted.", appEnv)
                return DevPhoneVerificationProvider(acceptAnyCode = true)
            }
            val service = env["TWILIO_VERIFY_SERVICE_SID"]?.takeIf { it.isNotBlank() }
            val keySid = env["TWILIO_API_KEY_SID"]?.takeIf { it.isNotBlank() }
            val keySecret = env["TWILIO_API_KEY_SECRET"]?.takeIf { it.isNotBlank() }
            if (service != null && keySid != null && keySecret != null) {
                return TwilioVerifyProvider(apiKeySid = keySid, apiKeySecret = keySecret, serviceSid = service)
            }
            check(appEnv != "production" && appEnv != "uat") {
                "Set TWILIO_VERIFY_SERVICE_SID, TWILIO_API_KEY_SID and TWILIO_API_KEY_SECRET when APP_ENV=$appEnv"
            }
            log.warn("Twilio is not configured: using the DEV phone verifier. Codes are written to this log, not sent.")
            return DevPhoneVerificationProvider()
        }

        /** Environments where the OTP step is not enforced. UAT and production always verify. */
        private val SKIP_VERIFICATION_ENVS = setOf("dev", "development", "sit", "local")
    }
}

/** The provider could not complete the call (network, auth, quota, unsupported destination, ...). */
class ProviderException(message: String, val rateLimited: Boolean = false, cause: Throwable? = null) : Exception(message, cause)
