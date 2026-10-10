package com.tana.spa.feature.auth.domain.model

import com.tana.spa.shared.auth.EmailError
import com.tana.spa.shared.auth.NameError
import com.tana.spa.shared.auth.PasswordError
import com.tana.spa.shared.auth.PhoneError

data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String,
    val phone: String? = null,
    val phoneVerified: Boolean = false,
)

data class Session(
    val accessToken: String,
    /** Absolute expiry as epoch milliseconds, so it stays meaningful after an app restart. */
    val expiresAtMillis: Long,
    val user: AuthUser,
)

/** Why a login or sign-up attempt failed; the UI maps each case to a localised message. */
enum class AuthFailure {
    INVALID_CREDENTIALS,
    EMAIL_TAKEN,

    /** Another account already verified this number. */
    PHONE_TAKEN,

    /** The server rejected the details (it checks phone numbers more strictly than the form can). */
    INVALID_INPUT,
    TOO_MANY_REQUESTS,
    NETWORK,
    SERVER,
}

sealed interface LoginResult {
    data class Success(val session: Session) : LoginResult

    /** The form was rejected locally, no request was sent. */
    data class InvalidInput(val email: EmailError?, val password: PasswordError?) : LoginResult

    data class Failure(val reason: AuthFailure) : LoginResult
}

sealed interface RegisterResult {
    data class Success(val session: Session) : RegisterResult

    /** The form was rejected locally, no request was sent. */
    data class InvalidInput(val name: NameError?, val email: EmailError?, val phone: PhoneError?, val password: PasswordError?) :
        RegisterResult

    data class Failure(val reason: AuthFailure) : RegisterResult
}

/** Why a phone-verification step failed. */
enum class OtpFailure {
    /** No valid login: the user must sign in again. */
    SESSION_EXPIRED,

    /** Another account already verified this number. */
    PHONE_CLAIMED,
    COUNTRY_UNSUPPORTED,

    /** The SMS provider is down; trying again shortly is safe. */
    UNAVAILABLE,

    /** The server has phone verification switched off; the step is skipped, not retried. */
    DISABLED,
    NETWORK,
    EXPIRED,
    TOO_MANY_ATTEMPTS,

    /** No code is outstanding (it was used, replaced, or never sent). */
    NOT_REQUESTED,
    SERVER,
}

sealed interface OtpSendResult {
    data class Sent(val resendAfterSeconds: Int, val expiresInSeconds: Int, val maskedPhone: String) : OtpSendResult
    data object AlreadyVerified : OtpSendResult
    data class RateLimited(val retryAfterSeconds: Int) : OtpSendResult
    data class Failed(val reason: OtpFailure) : OtpSendResult
}

sealed interface OtpVerifyResult {
    data object Verified : OtpVerifyResult
    data class WrongCode(val attemptsRemaining: Int?) : OtpVerifyResult
    data class Failed(val reason: OtpFailure) : OtpVerifyResult
}
