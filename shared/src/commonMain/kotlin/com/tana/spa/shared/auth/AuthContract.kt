package com.tana.spa.shared.auth

import kotlinx.serialization.Serializable

/** Wire contract of the auth API, shared by the Ktor server and every client target. */
object AuthApi {
    const val BASE = "/api/v1/auth"
    const val LOGIN = "$BASE/login"
    const val REGISTER = "$BASE/register"
    const val ME = "$BASE/me"
    const val PHONE_OTP_SEND = "$BASE/phone/otp/send"
    const val PHONE_OTP_VERIFY = "$BASE/phone/otp/verify"
}

@Serializable
data class LoginRequest(val email: String, val password: String)

/** [phone] is accepted as typed ("0333 433 969", "+84 333 433 969"); the server stores the normalised form. */
@Serializable
data class RegisterRequest(val displayName: String, val email: String, val phone: String, val password: String)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
    /** Normalised (+84…) phone number; null only for accounts created without one. */
    val phone: String? = null,
    /** True once the owner proved control of [phone] with a one-time code. */
    val phoneVerified: Boolean = false,
    /** `customer`, `staff` or `admin`. */
    val role: String = "customer",
)

/** The code is always sent to the phone number on the account, never to one supplied here. */
@Serializable
data class PhoneOtpSendResponse(
    /** Seconds before another code may be requested. */
    val resendAfterSeconds: Int,
    /** Seconds the code stays valid. */
    val expiresInSeconds: Int,
    /** e.g. `+84••••••969`, so the UI can say where the code went without exposing the number. */
    val maskedPhone: String,
)

@Serializable
data class PhoneOtpVerifyRequest(val code: String)

@Serializable
data class AuthResponse(val accessToken: String, val expiresInSeconds: Long, val user: UserDto)

/** Stable machine-readable [code]s let each client localise the message itself. */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    /** Set on 429s: seconds (an upper bound) until the client may retry. */
    val retryAfterSeconds: Int? = null,
    /** Set on a wrong one-time code: guesses left before a new code is required. */
    val attemptsRemaining: Int? = null,
)

object AuthErrorCodes {
    const val INVALID_REQUEST = "invalid_request"
    const val INVALID_CREDENTIALS = "invalid_credentials"
    const val EMAIL_TAKEN = "email_taken"
    const val PHONE_TAKEN = "phone_taken"
    const val PHONE_ALREADY_VERIFIED = "phone_already_verified"

    /** The number is already verified on a different account. */
    const val PHONE_CLAIMED = "phone_claimed"
    const val PHONE_COUNTRY_UNSUPPORTED = "phone_country_unsupported"
    const val OTP_NOT_REQUESTED = "otp_not_requested"
    const val OTP_INVALID = "otp_invalid"
    const val OTP_EXPIRED = "otp_expired"
    const val OTP_TOO_MANY_ATTEMPTS = "otp_too_many_attempts"

    /** The SMS provider is down or misconfigured; safe to retry later. */
    const val OTP_UNAVAILABLE = "otp_unavailable"

    /** Switched off by a feature flag. Retrying will not help; skip the feature instead. */
    const val FEATURE_DISABLED = "feature_disabled"

    /** No notification with that id on this account (or it belongs to someone else). */
    const val NOTIFICATION_NOT_FOUND = "notification_not_found"

    /** The push provider is down or misconfigured; the notification was still stored. */
    const val PUSH_UNAVAILABLE = "push_unavailable"

    /** A key that is not in this server's `FeatureFlag` catalog. */
    const val UNKNOWN_FLAG = "unknown_flag"
    const val TOO_MANY_REQUESTS = "too_many_requests"
    const val UNAUTHORIZED = "unauthorized"

    /** Signed in, but this account is not allowed to do that. */
    const val FORBIDDEN = "forbidden"
    const val INTERNAL = "internal_error"
}
