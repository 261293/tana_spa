package com.tana.spa

import com.tana.spa.auth.AuthConfig
import com.tana.spa.auth.AuthServices
import com.tana.spa.auth.PasswordHasher
import com.tana.spa.auth.Pbkdf2PasswordHasher
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.LoginRequest
import com.tana.spa.shared.auth.RegisterRequest
import com.tana.spa.shared.auth.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fast stand-in so the suite doesn't pay for 600k PBKDF2 rounds per call. */
private class FakeHasher : PasswordHasher {
    override fun hash(password: String) = "fake:$password"
    override fun verify(password: String, stored: String) = stored == "fake:$password"
}

class ApplicationTest {

    private fun services(rateLimit: Int = 100, trustProxy: Boolean = false) = AuthServices.create(
        config = AuthConfig(jwtSecret = "x".repeat(40), loginRateLimitPerMinute = rateLimit, trustProxy = trustProxy),
        hasher = FakeHasher(),
    )

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient { install(ContentNegotiation) { json() } }

    private suspend fun HttpClient.register(
        email: String = "user@example.com",
        password: String = "password123",
        phone: String = "0333433969",
    ) = post(AuthApi.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("Lan", email, phone, password))
    }

    private suspend fun HttpClient.login(email: String, password: String) = post(AuthApi.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(email, password))
    }

    @Test
    fun testRoot() = testApplication {
        application { module(services()) }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, Ktor!", response.bodyAsText())
    }

    @Test
    fun registerThenLoginReturnsTokenAndUser() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        assertEquals(HttpStatusCode.Created, client.register().status)

        // Email matching is case/whitespace-insensitive.
        val response = client.login("  User@Example.com ", "password123")
        assertEquals(HttpStatusCode.OK, response.status)
        val auth = response.body<AuthResponse>()
        assertTrue(auth.accessToken.isNotBlank())
        assertEquals("user@example.com", auth.user.email)
    }

    @Test
    fun wrongPasswordAndUnknownEmailGiveTheSameError() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        client.register()

        val wrongPassword = client.login("user@example.com", "bad-password")
        val unknownEmail = client.login("nobody@example.com", "password123")

        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(HttpStatusCode.Unauthorized, unknownEmail.status)
        assertEquals(wrongPassword.body<ErrorResponse>(), unknownEmail.body<ErrorResponse>())
        assertEquals(AuthErrorCodes.INVALID_CREDENTIALS, wrongPassword.body<ErrorResponse>().code)
    }

    @Test
    fun duplicateRegistrationIsRejected() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        client.register()

        val again = client.register(email = "USER@example.com")
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals(AuthErrorCodes.EMAIL_TAKEN, again.body<ErrorResponse>().code)
    }

    @Test
    fun phoneIsStoredInInternationalFormAndStartsUnverified() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        val created = client.register(phone = "0333 433 969")
        assertEquals(HttpStatusCode.Created, created.status)
        val user = created.body<AuthResponse>().user
        assertEquals("+84333433969", user.phone)
        assertEquals(false, user.phoneVerified)

        // An unverified claim does not lock the number: the real owner must still be able to sign up.
        val sameNumber = client.register(email = "other@example.com", phone = "+84 333 433 969")
        assertEquals(HttpStatusCode.Created, sameNumber.status)
    }

    @Test
    fun internationalNumbersAreAcceptedForEveryCountry() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        listOf(
            "+1 650-253-0000" to "+16502530000",
            "+44 20 7031 3000" to "+442070313000",
            "+61 2 9374 4000" to "+61293744000",
            "+65 6521 8000" to "+6565218000",
            "+81 3-6384-9000" to "+81363849000",
        ).forEachIndexed { i, (typed, stored) ->
            val response = client.register(email = "intl$i@example.com", phone = typed)
            assertEquals(HttpStatusCode.Created, response.status, "phone: $typed")
            assertEquals(stored, response.body<AuthResponse>().user.phone)
        }
    }

    @Test
    fun loginRateLimitIsPerClientNotGlobal() = testApplication {
        // trustProxy lets the test play different client addresses via X-Forwarded-For.
        application { module(services(rateLimit = 2, trustProxy = true)) }
        val client = jsonClient()
        suspend fun attempt(ip: String) = client.post(AuthApi.LOGIN) {
            header("X-Forwarded-For", ip)
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("user@example.com", "whatever1"))
        }.status

        repeat(2) { assertEquals(HttpStatusCode.Unauthorized, attempt("203.0.113.1")) }
        assertEquals(HttpStatusCode.TooManyRequests, attempt("203.0.113.1"))
        // A different client is unaffected by the first client's lockout.
        assertEquals(HttpStatusCode.Unauthorized, attempt("203.0.113.2"))
    }

    @Test
    fun invalidPhoneIsABadRequest() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        listOf("", "abc", "12345", "0133433969").forEach { phone ->
            assertEquals(HttpStatusCode.BadRequest, client.register(phone = phone).status, "phone: '$phone'")
        }
    }

    @Test
    fun weakPasswordAndMalformedBodyAreBadRequests() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        assertEquals(HttpStatusCode.BadRequest, client.register(password = "short").status)

        val malformed = client.post(AuthApi.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody("{not json")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }

    @Test
    fun meRequiresAValidToken() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        client.register()
        val token = client.login("user@example.com", "password123").body<AuthResponse>().accessToken

        assertEquals(HttpStatusCode.Unauthorized, client.get(AuthApi.ME).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get(AuthApi.ME) { bearerAuth("garbage.token.value") }.status,
        )

        val me = client.get(AuthApi.ME) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals("user@example.com", me.body<UserDto>().email)
    }

    @Test
    fun loginIsRateLimited() = testApplication {
        application { module(services(rateLimit = 3)) }
        val client = jsonClient()

        repeat(3) { client.login("user@example.com", "whatever1") }
        val blocked = client.login("user@example.com", "whatever1")
        assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
        assertEquals(AuthErrorCodes.TOO_MANY_REQUESTS, blocked.body<ErrorResponse>().code)
    }

    @Test
    fun corsAllowsConfiguredWebOriginOnly() = testApplication {
        application { module(services()) }
        val allowed = client.options(AuthApi.LOGIN) {
            header(HttpHeaders.Origin, "http://localhost:8080")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "Content-Type")
        }
        assertEquals("http://localhost:8080", allowed.headers[HttpHeaders.AccessControlAllowOrigin])

        val denied = client.options(AuthApi.LOGIN) {
            header(HttpHeaders.Origin, "http://evil.example")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertNull(denied.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun pbkdf2HasherRoundTrips() {
        val hasher = Pbkdf2PasswordHasher(iterations = 1_000)
        val stored = hasher.hash("s3cret-pass")
        assertTrue(hasher.verify("s3cret-pass", stored))
        assertFalse(hasher.verify("S3cret-pass", stored))
        assertFalse(hasher.verify("s3cret-pass", "garbage"))
        // Salted: hashing the same password twice never gives the same string.
        assertNotEquals(stored, hasher.hash("s3cret-pass"))
    }
}
