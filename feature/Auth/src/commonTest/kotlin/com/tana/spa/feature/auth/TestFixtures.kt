package com.tana.spa.feature.auth

import com.tana.spa.feature.auth.data.local.SessionStore
import com.tana.spa.feature.auth.data.remote.AuthRemoteApi
import com.tana.spa.feature.auth.domain.model.AuthUser
import com.tana.spa.feature.auth.domain.model.OtpSendResult
import com.tana.spa.feature.auth.domain.model.OtpVerifyResult
import com.tana.spa.feature.auth.domain.model.Session
import com.tana.spa.feature.auth.domain.repository.AuthOutcome
import com.tana.spa.feature.auth.domain.repository.AuthRepository
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.PhoneOtpSendResponse
import com.tana.spa.shared.auth.UserDto
import kotlinx.coroutines.CompletableDeferred

object TestFixtures {
    val user = AuthUser(id = "u1", email = "lan@example.com", displayName = "Lan", phone = "+84333433969")
    val session = Session(accessToken = "token", expiresAtMillis = 10_000_000L, user = user)
    val response = AuthResponse(
        accessToken = "token",
        expiresInSeconds = 3600,
        user = UserDto("u1", "lan@example.com", "Lan", "+84333433969"),
    )
    val otpSent = PhoneOtpSendResponse(resendAfterSeconds = 60, expiresInSeconds = 600, maskedPhone = "+84••••••969")
    val verifiedUser = UserDto("u1", "lan@example.com", "Lan", "+84333433969", phoneVerified = true)
}

class FakeSessionStore(var stored: Session? = null) : SessionStore {
    override fun save(session: Session) {
        stored = session
    }
    override fun load(): Session? = stored
    override fun clear() {
        stored = null
    }
}

/** Remote that either returns canned responses or throws the configured error; records calls. */
class FakeRemote(
    var response: AuthResponse = TestFixtures.response,
    /** Thrown by login and register. */
    var error: Exception? = null,
) : AuthRemoteApi {
    var sendResponse = TestFixtures.otpSent
    var sendError: Exception? = null
    var verifyResponse = TestFixtures.verifiedUser
    var verifyError: Exception? = null

    var lastEmail: String? = null
    var lastPassword: String? = null
    var calls = 0

    var lastRegister: List<String>? = null
    var registerCalls = 0
    var sendToken: String? = null
    var sendCalls = 0
    var verifyToken: String? = null
    var verifyCode: String? = null

    override suspend fun login(email: String, password: String): AuthResponse {
        calls++
        lastEmail = email
        lastPassword = password
        error?.let { throw it }
        return response
    }

    override suspend fun register(displayName: String, email: String, phone: String, password: String): AuthResponse {
        registerCalls++
        lastRegister = listOf(displayName, email, phone, password)
        error?.let { throw it }
        return response
    }

    override suspend fun sendPhoneOtp(accessToken: String): PhoneOtpSendResponse {
        sendCalls++
        sendToken = accessToken
        sendError?.let { throw it }
        return sendResponse
    }

    override suspend fun verifyPhoneOtp(accessToken: String, code: String): UserDto {
        verifyToken = accessToken
        verifyCode = code
        verifyError?.let { throw it }
        return verifyResponse
    }
}

/** Repository double for view-model tests: queue the results you want, count the calls. */
class FakeAuthRepository(var session: Session? = TestFixtures.session) : AuthRepository {
    val sendResults = ArrayDeque<OtpSendResult>()
    val verifyResults = ArrayDeque<OtpVerifyResult>()
    var registerOutcome: AuthOutcome = AuthOutcome.Success(TestFixtures.session)
    var sendCalls = 0
    var verifyCodes = mutableListOf<String>()
    var registerCalls = 0

    /** When set, `register` suspends until completed: lets a test hold a request "in flight". */
    var registerGate: CompletableDeferred<Unit>? = null

    override suspend fun login(email: String, password: String): AuthOutcome = AuthOutcome.Success(TestFixtures.session)

    override suspend fun register(displayName: String, email: String, phone: String, password: String): AuthOutcome {
        registerCalls++
        registerGate?.await()
        return registerOutcome
    }

    override fun currentSession(): Session? = session

    override suspend fun sendPhoneOtp(): OtpSendResult {
        sendCalls++
        return sendResults.removeFirstOrNull() ?: OtpSendResult.Sent(60, 600, "+84••••••969")
    }

    override suspend fun verifyPhoneOtp(code: String): OtpVerifyResult {
        verifyCodes += code
        return verifyResults.removeFirstOrNull() ?: OtpVerifyResult.Verified
    }
}
