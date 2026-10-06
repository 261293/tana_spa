package com.tana.spa.feature.auth.data.remote

import com.tana.spa.core.network.NetworkClient
import com.tana.spa.core.network.withDefaults
import com.tana.spa.feature.auth.data.remote.source.AuthRemoteApiImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthRemoteApiImplTest {

    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun api(handler: suspend (io.ktor.client.request.HttpRequestData) -> io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData): AuthRemoteApiImpl {
        val engine = MockEngine { request -> handler(request)(this) }
        return AuthRemoteApiImpl(NetworkClient(HttpClient(engine).withDefaults()), "http://api.test")
    }

    @Test
    fun postsCredentialsAndParsesSuccess() = runTest {
        var method: HttpMethod? = null
        var url: String? = null
        var body: String? = null
        val api = api { request ->
            method = request.method
            url = request.url.toString()
            body = request.body.toByteArray().decodeToString()
            val payload = """{"accessToken":"t","expiresInSeconds":60,"user":{"id":"1","email":"a@b.co","displayName":"A"},"extra":1}"""
            return@api { respond(payload, HttpStatusCode.OK, json) }
        }

        val response = api.login("a@b.co", "pw")

        assertEquals(HttpMethod.Post, method)
        assertEquals("http://api.test/api/v1/auth/login", url)
        assertEquals("""{"email":"a@b.co","password":"pw"}""", body)
        assertEquals("t", response.accessToken) // unknown "extra" key is ignored
    }

    @Test
    fun errorBodyCodeIsSurfaced() = runTest {
        val api = api {
            { respond("""{"code":"invalid_credentials","message":"nope"}""", HttpStatusCode.Unauthorized, json) }
        }

        val e = assertFailsWith<AuthApiException> { api.login("a@b.co", "pw") }
        assertEquals(401, e.status)
        assertEquals("invalid_credentials", e.code)
    }

    @Test
    fun unreadableErrorBodyStillReportsStatus() = runTest {
        val api = api { { respond("<html>Bad gateway</html>", HttpStatusCode.BadGateway) } }

        val e = assertFailsWith<AuthApiException> { api.login("a@b.co", "pw") }
        assertEquals(502, e.status)
        assertEquals(null, e.code)
    }

    @Test
    fun registerPostsAllFourFieldsAndParsesTheSession() = runTest {
        var url: String? = null
        var body: String? = null
        val api = api { request ->
            url = request.url.toString()
            body = request.body.toByteArray().decodeToString()
            val payload = """{"accessToken":"t","expiresInSeconds":60,"user":{"id":"1","email":"a@b.co","displayName":"Phương Anh","phone":"+84333433969","phoneVerified":false}}"""
            return@api { respond(payload, HttpStatusCode.Created, json) }
        }

        val response = api.register("Phương Anh", "a@b.co", "0333 433 969", "password123")

        assertEquals("http://api.test/api/v1/auth/register", url)
        assertEquals(
            """{"displayName":"Phương Anh","email":"a@b.co","phone":"0333 433 969","password":"password123"}""",
            body,
        )
        assertEquals("+84333433969", response.user.phone)
    }

    @Test
    fun sendOtpIsAnAuthenticatedPostWithNoPhoneNumberInIt() = runTest {
        var auth: String? = null
        var url: String? = null
        var body: String? = null
        val api = api { request ->
            auth = request.headers[HttpHeaders.Authorization]
            url = request.url.toString()
            body = request.body.toByteArray().decodeToString()
            return@api { respond("""{"resendAfterSeconds":60,"expiresInSeconds":600,"maskedPhone":"+84••••••969"}""", HttpStatusCode.Accepted, json) }
        }

        val sent = api.sendPhoneOtp("secret-token")

        assertEquals("Bearer secret-token", auth)
        assertEquals("http://api.test/api/v1/auth/phone/otp/send", url)
        // The destination is never client-supplied: the request carries no number at all.
        assertEquals("", body)
        assertEquals("+84••••••969", sent.maskedPhone)
    }

    @Test
    fun verifyOtpSendsTheCodeWithTheTokenAndReturnsTheUser() = runTest {
        var auth: String? = null
        var body: String? = null
        val api = api { request ->
            auth = request.headers[HttpHeaders.Authorization]
            body = request.body.toByteArray().decodeToString()
            return@api { respond("""{"id":"1","email":"a@b.co","displayName":"A","phone":"+84333433969","phoneVerified":true}""", HttpStatusCode.OK, json) }
        }

        val user = api.verifyPhoneOtp("tok", "123456")

        assertEquals("Bearer tok", auth)
        assertEquals("""{"code":"123456"}""", body)
        assertEquals(true, user.phoneVerified)
    }

    @Test
    fun errorHintsRetryAfterAndAttemptsRemainingAreSurfaced() = runTest {
        val rateLimited = api {
            { respond("""{"code":"too_many_requests","message":"x","retryAfterSeconds":42}""", HttpStatusCode.TooManyRequests, json) }
        }
        val e1 = assertFailsWith<AuthApiException> { rateLimited.sendPhoneOtp("t") }
        assertEquals(429, e1.status)
        assertEquals(42, e1.retryAfterSeconds)

        val wrongCode = api {
            { respond("""{"code":"otp_invalid","message":"x","attemptsRemaining":3}""", HttpStatusCode.BadRequest, json) }
        }
        val e2 = assertFailsWith<AuthApiException> { wrongCode.verifyPhoneOtp("t", "000000") }
        assertEquals("otp_invalid", e2.code)
        assertEquals(3, e2.attemptsRemaining)
    }
}
