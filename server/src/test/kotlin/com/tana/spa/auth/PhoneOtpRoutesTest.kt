package com.tana.spa.auth

import com.tana.spa.auth.otp.DevPhoneVerificationProvider
import com.tana.spa.auth.otp.OtpConfig
import com.tana.spa.auth.otp.PhoneVerificationProvider
import com.tana.spa.auth.otp.ProviderException
import com.tana.spa.module
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.PhoneOtpSendResponse
import com.tana.spa.shared.auth.PhoneOtpVerifyRequest
import com.tana.spa.shared.auth.RegisterRequest
import com.tana.spa.shared.auth.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The OTP HTTP contract: status codes, error codes and the retry hints clients rely on. */
class PhoneOtpRoutesTest {

    private class FakeHasher : PasswordHasher {
        override fun hash(password: String) = "fake:$password"
        override fun verify(password: String, stored: String) = stored == "fake:$password"
    }

    private val dev = DevPhoneVerificationProvider()

    private fun services(
        provider: PhoneVerificationProvider = dev,
        otp: OtpConfig = OtpConfig(),
    ) = AuthServices.create(
        config = AuthConfig(jwtSecret = "x".repeat(40)),
        hasher = FakeHasher(),
        otpProvider = provider,
        otpConfig = otp,
    )

    private fun ApplicationTestBuilder.jsonClient(): HttpClient =
        createClient { install(ContentNegotiation) { json() } }

    private suspend fun HttpClient.signUp(email: String, phone: String): AuthResponse =
        post(AuthApi.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("Lan", email, phone, "password123"))
        }.body()

    private suspend fun HttpClient.sendOtp(token: String?, language: String? = null): HttpResponse =
        post(AuthApi.PHONE_OTP_SEND) {
            token?.let { bearerAuth(it) }
            language?.let { header(HttpHeaders.AcceptLanguage, it) }
        }

    private suspend fun HttpClient.verifyOtp(token: String, code: String): HttpResponse =
        post(AuthApi.PHONE_OTP_VERIFY) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PhoneOtpVerifyRequest(code))
        }

    @Test
    fun bothEndpointsRequireAuthentication() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        assertEquals(HttpStatusCode.Unauthorized, client.sendOtp(null).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post(AuthApi.PHONE_OTP_VERIFY) {
                contentType(ContentType.Application.Json)
                setBody(PhoneOtpVerifyRequest("123456"))
            }.status,
        )
    }

    @Test
    fun fullFlowSendsToTheAccountsOwnNumberAndVerifiesIt() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val auth = client.signUp("a@example.com", "0333 433 969")

        val sent = client.sendOtp(auth.accessToken, language = "vi-VN,vi;q=0.9,en;q=0.8")
        assertEquals(HttpStatusCode.Accepted, sent.status)
        val body = sent.body<PhoneOtpSendResponse>()
        assertEquals(60, body.resendAfterSeconds)
        assertEquals(600, body.expiresInSeconds)
        assertEquals("+84••••••969", body.maskedPhone)
        // The full number is never echoed back.
        assertNull(sent.bodyAsText().takeIf { "+84333433969" in it })

        val code = assertNotNull(dev.peekCode("+84333433969"))
        val verified = client.verifyOtp(auth.accessToken, code)
        assertEquals(HttpStatusCode.OK, verified.status)
        assertEquals(true, verified.body<UserDto>().phoneVerified)
    }

    @Test
    fun wrongCodeReportsRemainingAttemptsThenLocksOut() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val token = client.signUp("b@example.com", "0333433969").accessToken
        client.sendOtp(token)
        val wrong = if (dev.peekCode("+84333433969") == "000000") "111111" else "000000"

        val first = client.verifyOtp(token, wrong)
        assertEquals(HttpStatusCode.BadRequest, first.status)
        val err = first.body<ErrorResponse>()
        assertEquals(AuthErrorCodes.OTP_INVALID, err.code)
        assertEquals(4, err.attemptsRemaining)

        repeat(3) { client.verifyOtp(token, wrong) }
        val locked = client.verifyOtp(token, wrong)
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertEquals(AuthErrorCodes.OTP_TOO_MANY_ATTEMPTS, locked.body<ErrorResponse>().code)
    }

    @Test
    fun verifyBeforeRequestingIsNotFoundAndMalformedBodiesAreBadRequests() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val token = client.signUp("c@example.com", "0333433969").accessToken

        val none = client.verifyOtp(token, "123456")
        assertEquals(HttpStatusCode.NotFound, none.status)
        assertEquals(AuthErrorCodes.OTP_NOT_REQUESTED, none.body<ErrorResponse>().code)

        val malformed = client.post(AuthApi.PHONE_OTP_VERIFY) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("{nope")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }

    @Test
    fun resendWithinTheCooldownIs429WithARetryHintAndHeader() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val token = client.signUp("d@example.com", "0333433969").accessToken
        client.sendOtp(token)

        val again = client.sendOtp(token)
        assertEquals(HttpStatusCode.TooManyRequests, again.status)
        val err = again.body<ErrorResponse>()
        assertEquals(AuthErrorCodes.TOO_MANY_REQUESTS, err.code)
        assertEquals(60, err.retryAfterSeconds) // the body survives the 429 status page
        assertEquals("60", again.headers[HttpHeaders.RetryAfter])
    }

    @Test
    fun alreadyVerifiedAccountsGetAConflict() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val token = client.signUp("e@example.com", "0333433969").accessToken
        client.sendOtp(token)
        client.verifyOtp(token, assertNotNull(dev.peekCode("+84333433969")))

        val again = client.sendOtp(token)
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals(AuthErrorCodes.PHONE_ALREADY_VERIFIED, again.body<ErrorResponse>().code)
    }

    @Test
    fun registeringWithAnAlreadyVerifiedNumberIsRefused() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val owner = client.signUp("f1@example.com", "0333433969").accessToken
        client.sendOtp(owner)
        client.verifyOtp(owner, assertNotNull(dev.peekCode("+84333433969")))

        val blocked = client.post(AuthApi.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("Squatter", "f2@example.com", "0333433969", "password123"))
        }
        assertEquals(HttpStatusCode.Conflict, blocked.status)
        assertEquals(AuthErrorCodes.PHONE_TAKEN, blocked.body<ErrorResponse>().code)
    }

    @Test
    fun unsupportedCountriesAreRefusedWithAClearCode() = testApplication {
        application { module(services(otp = OtpConfig(allowedCountries = setOf("US")))) }
        val client = jsonClient()
        val token = client.signUp("g@example.com", "0333433969").accessToken

        val refused = client.sendOtp(token)
        assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
        assertEquals(AuthErrorCodes.PHONE_COUNTRY_UNSUPPORTED, refused.body<ErrorResponse>().code)
    }

    @Test
    fun otpWorksForNumbersFromOtherCountries() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val token = client.signUp("h@example.com", "+1 650-253-0000").accessToken

        assertEquals(HttpStatusCode.Accepted, client.sendOtp(token).status)
        val verified = client.verifyOtp(token, assertNotNull(dev.peekCode("+16502530000")))
        assertEquals(HttpStatusCode.OK, verified.status)
        assertEquals("+16502530000", verified.body<UserDto>().phone)
    }

    @Test
    fun aProviderOutageIs503AndNeverLeaksDetails() = testApplication {
        val down = object : PhoneVerificationProvider {
            override val name = "down"
            override fun start(phoneE164: String, locale: String?): String? = throw ProviderException("secret internal detail")
            override fun check(phoneE164: String, code: String): Boolean = throw ProviderException("secret internal detail")
        }
        application { module(services(provider = down, otp = OtpConfig(resendCooldown = Duration.ZERO))) }
        val client = jsonClient()
        val token = client.signUp("i@example.com", "0333433969").accessToken

        val response = client.sendOtp(token)
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(AuthErrorCodes.OTP_UNAVAILABLE, response.body<ErrorResponse>().code)
        assertNull(response.bodyAsText().takeIf { "secret internal detail" in it })
    }
}
