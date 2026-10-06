package com.tana.spa.feature.auth.data.repository

import com.tana.spa.feature.auth.FakeRemote
import com.tana.spa.feature.auth.FakeSessionStore
import com.tana.spa.feature.auth.TestFixtures
import com.tana.spa.feature.auth.data.remote.AuthApiException
import com.tana.spa.feature.auth.domain.model.AuthFailure
import com.tana.spa.feature.auth.domain.repository.AuthOutcome
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.feature.auth.domain.model.OtpFailure
import com.tana.spa.feature.auth.domain.model.OtpSendResult
import com.tana.spa.feature.auth.domain.model.OtpVerifyResult
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AuthRepositoryImplTest {

    private val now = 1_000_000L
    private val store = FakeSessionStore()
    private val remote = FakeRemote()
    private val repository = AuthRepositoryImpl(remote, store) { now }

    @Test
    fun successfulLoginPersistsSessionWithAbsoluteExpiry() = runTest {
        val outcome = repository.login("lan@example.com", "password123")

        val success = assertIs<AuthOutcome.Success>(outcome)
        assertEquals(now + 3600 * 1000, success.session.expiresAtMillis)
        assertEquals(success.session, store.stored)
    }

    @Test
    fun invalidCredentialsAreMapped() = runTest {
        remote.error = AuthApiException(401, AuthErrorCodes.INVALID_CREDENTIALS)
        assertEquals(AuthOutcome.Failure(AuthFailure.INVALID_CREDENTIALS), repository.login("a@b.co", "x"))
        assertNull(store.stored)
    }

    @Test
    fun rateLimitIsMapped() = runTest {
        remote.error = AuthApiException(429, null)
        assertEquals(AuthOutcome.Failure(AuthFailure.TOO_MANY_REQUESTS), repository.login("a@b.co", "x"))
    }

    @Test
    fun otherServerErrorsAreServerFailures() = runTest {
        remote.error = AuthApiException(502, null)
        assertEquals(AuthOutcome.Failure(AuthFailure.SERVER), repository.login("a@b.co", "x"))
    }

    @Test
    fun transportErrorsAreNetworkFailures() = runTest {
        remote.error = RuntimeException("no route to host")
        assertEquals(AuthOutcome.Failure(AuthFailure.NETWORK), repository.login("a@b.co", "x"))
    }

    @Test
    fun expiredSessionIsDiscardedAndCleared() {
        store.stored = TestFixtures.session.copy(expiresAtMillis = now)
        assertNull(repository.currentSession())
        assertNull(store.stored)
    }

    @Test
    fun validSessionIsReturned() {
        store.stored = TestFixtures.session.copy(expiresAtMillis = now + 1)
        assertEquals(store.stored, repository.currentSession())
    }

    // ---- register ----

    @Test
    fun registerSavesTheSessionLikeLogin() = runTest {
        val outcome = repository.register("Lan", "lan@example.com", "0333433969", "password123")

        val success = assertIs<AuthOutcome.Success>(outcome)
        assertEquals(store.stored, success.session)
        assertEquals(listOf("Lan", "lan@example.com", "0333433969", "password123"), remote.lastRegister)
    }

    @Test
    fun registerFailuresAreMapped() = runTest {
        suspend fun failure(status: Int, code: String?): AuthFailure {
            remote.error = AuthApiException(status, code)
            return assertIs<AuthOutcome.Failure>(repository.register("L", "a@b.co", "0333433969", "password123")).reason
        }
        assertEquals(AuthFailure.EMAIL_TAKEN, failure(409, AuthErrorCodes.EMAIL_TAKEN))
        assertEquals(AuthFailure.PHONE_TAKEN, failure(409, AuthErrorCodes.PHONE_TAKEN))
        assertEquals(AuthFailure.INVALID_INPUT, failure(400, AuthErrorCodes.INVALID_REQUEST))
        assertEquals(AuthFailure.TOO_MANY_REQUESTS, failure(429, null))
        assertEquals(AuthFailure.SERVER, failure(500, null))
        assertNull(store.stored)
    }

    // ---- phone OTP ----

    private fun signedIn() { store.stored = TestFixtures.session.copy(expiresAtMillis = now + 1) }

    @Test
    fun sendingWithoutASessionNeedsALogin() = runTest {
        assertEquals(OtpSendResult.Failed(OtpFailure.SESSION_EXPIRED), repository.sendPhoneOtp())
        assertEquals(0, remote.sendCalls)
    }

    @Test
    fun sendUsesTheStoredAccessToken() = runTest {
        signedIn()
        val result = assertIs<OtpSendResult.Sent>(repository.sendPhoneOtp())

        assertEquals("token", remote.sendToken)
        assertEquals(60, result.resendAfterSeconds)
        assertEquals("+84••••••969", result.maskedPhone)
    }

    @Test
    fun aRateLimitCarriesTheServersRetryHint() = runTest {
        signedIn()
        remote.sendError = AuthApiException(429, AuthErrorCodes.TOO_MANY_REQUESTS, retryAfterSeconds = 37)
        assertEquals(OtpSendResult.RateLimited(37), repository.sendPhoneOtp())

        remote.sendError = AuthApiException(429, null)
        assertEquals(OtpSendResult.RateLimited(60), repository.sendPhoneOtp())
    }

    @Test
    fun alreadyVerifiedUpdatesTheLocalSession() = runTest {
        signedIn()
        remote.sendError = AuthApiException(409, AuthErrorCodes.PHONE_ALREADY_VERIFIED)

        assertEquals(OtpSendResult.AlreadyVerified, repository.sendPhoneOtp())
        assertTrue(store.stored!!.user.phoneVerified)
    }

    @Test
    fun aRejectedTokenClearsTheSession() = runTest {
        signedIn()
        remote.sendError = AuthApiException(401, AuthErrorCodes.UNAUTHORIZED)

        assertEquals(OtpSendResult.Failed(OtpFailure.SESSION_EXPIRED), repository.sendPhoneOtp())
        assertNull(store.stored)
    }

    @Test
    fun sendFailuresAreClassified() = runTest {
        signedIn()
        suspend fun failure(status: Int, code: String?): OtpSendResult {
            remote.sendError = AuthApiException(status, code)
            return repository.sendPhoneOtp()
        }
        assertEquals(OtpSendResult.Failed(OtpFailure.UNAVAILABLE), failure(503, AuthErrorCodes.OTP_UNAVAILABLE))
        assertEquals(OtpSendResult.Failed(OtpFailure.COUNTRY_UNSUPPORTED), failure(422, AuthErrorCodes.PHONE_COUNTRY_UNSUPPORTED))
        assertEquals(OtpSendResult.Failed(OtpFailure.PHONE_CLAIMED), failure(409, AuthErrorCodes.PHONE_CLAIMED))
        assertEquals(OtpSendResult.Failed(OtpFailure.SERVER), failure(500, null))

        remote.sendError = RuntimeException("offline")
        assertEquals(OtpSendResult.Failed(OtpFailure.NETWORK), repository.sendPhoneOtp())
    }

    @Test
    fun verifyingSavesTheVerifiedUserLocally() = runTest {
        signedIn()
        assertEquals(OtpVerifyResult.Verified, repository.verifyPhoneOtp("123456"))

        assertEquals("123456", remote.verifyCode)
        assertEquals("token", remote.verifyToken)
        assertTrue(store.stored!!.user.phoneVerified)
        assertEquals("token", store.stored!!.accessToken, "the login itself must survive")
    }

    @Test
    fun aWrongCodeReportsTheRemainingAttempts() = runTest {
        signedIn()
        remote.verifyError = AuthApiException(400, AuthErrorCodes.OTP_INVALID, attemptsRemaining = 3)

        assertEquals(OtpVerifyResult.WrongCode(3), repository.verifyPhoneOtp("000000"))
        assertEquals(false, store.stored!!.user.phoneVerified)
    }

    @Test
    fun verifyFailuresAreClassified() = runTest {
        signedIn()
        suspend fun failure(status: Int, code: String?): OtpVerifyResult {
            remote.verifyError = AuthApiException(status, code)
            return repository.verifyPhoneOtp("123456")
        }
        assertEquals(OtpVerifyResult.Failed(OtpFailure.EXPIRED), failure(400, AuthErrorCodes.OTP_EXPIRED))
        assertEquals(OtpVerifyResult.Failed(OtpFailure.TOO_MANY_ATTEMPTS), failure(429, AuthErrorCodes.OTP_TOO_MANY_ATTEMPTS))
        assertEquals(OtpVerifyResult.Failed(OtpFailure.NOT_REQUESTED), failure(404, AuthErrorCodes.OTP_NOT_REQUESTED))
        assertEquals(OtpVerifyResult.Failed(OtpFailure.PHONE_CLAIMED), failure(409, AuthErrorCodes.PHONE_CLAIMED))
        assertEquals(OtpVerifyResult.Failed(OtpFailure.UNAVAILABLE), failure(503, AuthErrorCodes.OTP_UNAVAILABLE))

        remote.verifyError = RuntimeException("offline")
        assertEquals(OtpVerifyResult.Failed(OtpFailure.NETWORK), repository.verifyPhoneOtp("123456"))
    }

    @Test
    fun verifyingWithoutASessionNeedsALogin() = runTest {
        assertEquals(OtpVerifyResult.Failed(OtpFailure.SESSION_EXPIRED), repository.verifyPhoneOtp("123456"))
    }
}
