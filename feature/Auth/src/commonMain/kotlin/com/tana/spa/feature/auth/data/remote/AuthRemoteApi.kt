package com.tana.spa.feature.auth.data.remote

import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.PhoneOtpSendResponse
import com.tana.spa.shared.auth.UserDto

interface AuthRemoteApi {
    /** @throws AuthApiException when the server answers with a non-2xx status. */
    suspend fun login(email: String, password: String): AuthResponse

    suspend fun register(displayName: String, email: String, phone: String, password: String): AuthResponse

    /** Spends [refreshToken]; the answer carries its replacement. */
    suspend fun refresh(refreshToken: String): AuthResponse

    /** Ends the sign-in [accessToken] belongs to, and with it that sign-in's refresh token. */
    suspend fun logout(accessToken: String)

    /** Asks the server to text a code to the phone number already on the account. */
    suspend fun sendPhoneOtp(accessToken: String): PhoneOtpSendResponse

    suspend fun verifyPhoneOtp(accessToken: String, code: String): UserDto
}

/** A well-formed error answer from the server, as opposed to a transport failure. */
class AuthApiException(
    val status: Int,
    val code: String?,
    /** From a 429: an upper bound on how long to wait. */
    val retryAfterSeconds: Int? = null,
    /** From a wrong one-time code: guesses left before a new code is needed. */
    val attemptsRemaining: Int? = null,
) : Exception("Auth API error $status ($code)")
