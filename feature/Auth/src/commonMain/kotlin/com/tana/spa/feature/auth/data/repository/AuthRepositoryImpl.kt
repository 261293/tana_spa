package com.tana.spa.feature.auth.data.repository

import com.tana.spa.core.logging.AppLog
import com.tana.spa.feature.auth.data.local.SessionStore
import com.tana.spa.feature.auth.data.remote.AuthApiException
import com.tana.spa.feature.auth.data.remote.AuthRemoteApi
import com.tana.spa.feature.auth.domain.model.AuthFailure
import com.tana.spa.feature.auth.domain.model.AuthUser
import com.tana.spa.feature.auth.domain.model.OtpFailure
import com.tana.spa.feature.auth.domain.model.OtpSendResult
import com.tana.spa.feature.auth.domain.model.OtpVerifyResult
import com.tana.spa.feature.auth.domain.model.Session
import com.tana.spa.feature.auth.domain.model.UserRoleName
import com.tana.spa.feature.auth.domain.repository.AuthOutcome
import com.tana.spa.feature.auth.domain.repository.AuthRepository
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.UserDto
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

class AuthRepositoryImpl(
    private val remote: AuthRemoteApi,
    private val store: SessionStore,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : AuthRepository {

    override suspend fun login(email: String, password: String): AuthOutcome = authenticate { remote.login(email, password) }

    override suspend fun register(displayName: String, email: String, phone: String, password: String): AuthOutcome =
        authenticate { remote.register(displayName, email, phone, password) }

    private suspend fun authenticate(call: suspend () -> AuthResponse): AuthOutcome = try {
        val session = call().toSession(nowMillis())
        store.save(session)
        AppLog.i(TAG) { "authenticated user=${session.user.id} phoneVerified=${session.user.phoneVerified}" }
        AuthOutcome.Success(session)
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthApiException) {
        val failure = e.toAuthFailure()
        AppLog.w(TAG) { "authentication rejected: ${e.describe()} -> $failure" }
        AuthOutcome.Failure(failure)
    } catch (e: Throwable) {
        // Ktor's wasm engine reports a failed fetch as kotlin.Error, which is not an Exception.
        // No response at all: offline, DNS, TLS, timeout, or an undecodable body.
        AppLog.w(TAG, e) { "authentication failed without a usable response: ${e::class.simpleName}" }
        AuthOutcome.Failure(AuthFailure.NETWORK)
    }

    override fun currentSession(): Session? {
        val session = store.load() ?: return null
        if (session.expiresAtMillis <= nowMillis()) {
            AppLog.i(TAG) { "stored session for user=${session.user.id} has expired, clearing" }
            store.clear()
            return null
        }
        return session
    }

    override suspend fun sendPhoneOtp(): OtpSendResult {
        val session = currentSession() ?: run {
            AppLog.w(TAG) { "cannot send OTP: no valid session" }
            return OtpSendResult.Failed(OtpFailure.SESSION_EXPIRED)
        }
        return try {
            val sent = remote.sendPhoneOtp(session.accessToken)
            OtpSendResult.Sent(sent.resendAfterSeconds, sent.expiresInSeconds, sent.maskedPhone)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthApiException) {
            when {
                e.code == AuthErrorCodes.PHONE_ALREADY_VERIFIED -> {
                    AppLog.i(TAG) { "OTP send: server reports phone already verified, updating local session" }
                    markVerifiedLocally(session)
                    OtpSendResult.AlreadyVerified
                }

                e.status == 429 -> OtpSendResult.RateLimited(e.retryAfterSeconds ?: DEFAULT_RETRY_SECONDS)

                else -> OtpSendResult.Failed(e.toOtpFailure()).also {
                    AppLog.w(TAG) { "OTP send rejected: ${e.describe()} -> ${it.reason}" }
                }
            }
        } catch (e: Throwable) {
            AppLog.w(TAG, e) { "OTP send failed without a usable response: ${e::class.simpleName}" }
            OtpSendResult.Failed(OtpFailure.NETWORK)
        }
    }

    override suspend fun verifyPhoneOtp(code: String): OtpVerifyResult {
        val session = currentSession() ?: run {
            AppLog.w(TAG) { "cannot verify OTP: no valid session" }
            return OtpVerifyResult.Failed(OtpFailure.SESSION_EXPIRED)
        }
        return try {
            val user = remote.verifyPhoneOtp(session.accessToken, code)
            store.save(session.copy(user = user.toAuthUser()))
            OtpVerifyResult.Verified
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthApiException) {
            when (e.code) {
                AuthErrorCodes.OTP_INVALID -> OtpVerifyResult.WrongCode(e.attemptsRemaining)

                else -> OtpVerifyResult.Failed(e.toOtpFailure()).also {
                    AppLog.w(TAG) { "OTP verify rejected: ${e.describe()} -> ${it.reason}" }
                }
            }
        } catch (e: Throwable) {
            AppLog.w(TAG, e) { "OTP verify failed without a usable response: ${e::class.simpleName}" }
            OtpVerifyResult.Failed(OtpFailure.NETWORK)
        }
    }

    private fun markVerifiedLocally(session: Session) {
        store.save(session.copy(user = session.user.copy(phoneVerified = true)))
    }

    private fun AuthApiException.toOtpFailure(): OtpFailure = when {
        status == 401 -> {
            // The token was rejected (expired or revoked): the stored session is useless.
            AppLog.w(TAG) { "server rejected the access token (401), clearing stored session" }
            store.clear()
            OtpFailure.SESSION_EXPIRED
        }

        code == AuthErrorCodes.PHONE_CLAIMED -> OtpFailure.PHONE_CLAIMED

        code == AuthErrorCodes.PHONE_COUNTRY_UNSUPPORTED -> OtpFailure.COUNTRY_UNSUPPORTED

        code == AuthErrorCodes.OTP_UNAVAILABLE -> OtpFailure.UNAVAILABLE

        code == AuthErrorCodes.FEATURE_DISABLED -> OtpFailure.DISABLED

        code == AuthErrorCodes.OTP_EXPIRED -> OtpFailure.EXPIRED

        code == AuthErrorCodes.OTP_TOO_MANY_ATTEMPTS -> OtpFailure.TOO_MANY_ATTEMPTS

        code == AuthErrorCodes.OTP_NOT_REQUESTED -> OtpFailure.NOT_REQUESTED

        else -> OtpFailure.SERVER
    }

    private companion object {
        const val TAG = "AuthRepo"
        const val DEFAULT_RETRY_SECONDS = 60
    }
}

private fun AuthApiException.describe() = "status=$status code=$code"

private fun AuthApiException.toAuthFailure(): AuthFailure = when {
    status == 429 || code == AuthErrorCodes.TOO_MANY_REQUESTS -> AuthFailure.TOO_MANY_REQUESTS
    code == AuthErrorCodes.EMAIL_TAKEN -> AuthFailure.EMAIL_TAKEN
    code == AuthErrorCodes.PHONE_TAKEN -> AuthFailure.PHONE_TAKEN
    code == AuthErrorCodes.INVALID_REQUEST || status == 400 -> AuthFailure.INVALID_INPUT
    status == 401 || code == AuthErrorCodes.INVALID_CREDENTIALS -> AuthFailure.INVALID_CREDENTIALS
    else -> AuthFailure.SERVER
}

private fun UserDto.toAuthUser() = AuthUser(id, email, displayName, phone, phoneVerified, UserRoleName.fromWire(role))

private fun AuthResponse.toSession(nowMillis: Long) = Session(
    accessToken = accessToken,
    expiresAtMillis = nowMillis + expiresInSeconds * 1000,
    user = user.toAuthUser(),
)
