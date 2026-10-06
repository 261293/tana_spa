package com.tana.spa.feature.auth.domain.repository

import com.tana.spa.feature.auth.domain.model.AuthFailure
import com.tana.spa.feature.auth.domain.model.OtpSendResult
import com.tana.spa.feature.auth.domain.model.OtpVerifyResult
import com.tana.spa.feature.auth.domain.model.Session

/** None of these throw for expected failures (offline, wrong password, rate limited, ...). */
interface AuthRepository {
    /** Authenticates and, on success, persists the session. */
    suspend fun login(email: String, password: String): AuthOutcome

    /** Creates the account and, on success, persists the session (the user is signed in). */
    suspend fun register(displayName: String, email: String, phone: String, password: String): AuthOutcome

    /** The stored session, or null when there is none or it has expired. */
    fun currentSession(): Session?

    /** Texts a code to the signed-in user's own phone number. */
    suspend fun sendPhoneOtp(): OtpSendResult

    /** Checks the code; on success the stored session is updated to `phoneVerified = true`. */
    suspend fun verifyPhoneOtp(code: String): OtpVerifyResult
}

sealed interface AuthOutcome {
    data class Success(val session: Session) : AuthOutcome
    data class Failure(val reason: AuthFailure) : AuthOutcome
}
