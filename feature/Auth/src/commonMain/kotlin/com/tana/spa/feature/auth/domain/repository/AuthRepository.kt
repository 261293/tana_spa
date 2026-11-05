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

    /**
     * The stored session, or null when there is none or it can no longer be refreshed. The access
     * token inside may already have expired: the next call that is refused refreshes it.
     */
    fun currentSession(): Session?

    /** Spends the stored refresh token for a fresh pair. Null when the guest has to sign in again. */
    suspend fun refreshSession(): Session?

    /**
     * Asks Supabase Auth to end the sign-in [accessToken] belongs to, so its refresh token cannot
     * outlive the sign-out. Best effort: clearing the session locally is the caller's job and must
     * not wait on this.
     */
    suspend fun revokeSession(accessToken: String)

    /** Texts a code to the signed-in user's own phone number. */
    suspend fun sendPhoneOtp(): OtpSendResult

    /** Checks the code; on success the stored session is updated to `phoneVerified = true`. */
    suspend fun verifyPhoneOtp(code: String): OtpVerifyResult
}

sealed interface AuthOutcome {
    data class Success(val session: Session) : AuthOutcome
    data class Failure(val reason: AuthFailure) : AuthOutcome
}
