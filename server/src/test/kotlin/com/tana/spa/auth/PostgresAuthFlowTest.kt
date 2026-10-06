package com.tana.spa.auth

import com.tana.spa.auth.otp.DevPhoneVerificationProvider
import com.tana.spa.auth.otp.PostgresPhoneVerificationRepository
import com.tana.spa.db.TestDatabase
import com.tana.spa.module
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.LoginRequest
import com.tana.spa.shared.auth.PhoneOtpSendResponse
import com.tana.spa.shared.auth.PhoneOtpVerifyRequest
import com.tana.spa.shared.auth.RegisterRequest
import com.tana.spa.shared.auth.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** The real HTTP API on top of the real PostgreSQL repository: no in-memory shortcuts. */
class PostgresAuthFlowTest {

    private val dataSource = TestDatabase.dataSource

    private fun services() = AuthServices.create(
        config = AuthConfig(jwtSecret = "x".repeat(40)),
        users = PostgresUserRepository(dataSource),
        hasher = Pbkdf2PasswordHasher(iterations = 1_000),
    )

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient { install(ContentNegotiation) { json() } }

    private fun email() = "u${UUID.randomUUID().toString().take(10)}@example.com"

    // A real, libphonenumber-valid Vietnamese mobile number: 039 + 7 digits.
    private var seq = (System.nanoTime() % 8_000_000L).toInt()
    private fun phoneLocal() = "039" + (1_000_000 + (seq++ % 9_000_000)).toString()

    private suspend fun HttpClient.register(email: String, phone: String, password: String = "password123"): HttpResponse =
        post(AuthApi.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("Nguyễn Lê Phương Anh", email, phone, password))
        }

    private suspend fun HttpClient.login(email: String, password: String = "password123"): HttpResponse = post(AuthApi.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(email, password))
    }

    @Test
    fun registerLoginAndMeWorkEndToEndAndSurviveARestart() = testApplication {
        val email = email()
        val phone = phoneLocal()
        application { module(services()) }
        val client = jsonClient()

        val registered = client.register(email, phone)
        assertEquals(HttpStatusCode.Created, registered.status)
        val user = registered.body<AuthResponse>().user
        assertEquals("+84" + phone.drop(1), user.phone) // stored and returned in normalised form
        assertEquals("Nguyễn Lê Phương Anh", user.displayName)

        val token = client.login(email).body<AuthResponse>().accessToken
        val me = client.get(AuthApi.ME) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(user.id, me.body<UserDto>().id)

        // A brand-new service graph over the same database (= a server restart) still knows the
        // user, and, because the signing secret is unchanged, still accepts the earlier token.
        val restarted = services()
        assertEquals(user.id, (restarted.authService.login(email, "password123") as AuthResult.Success).user.id)
        assertEquals(user.id, restarted.tokens.verifier.verify(token).subject)
    }

    @Test
    fun aVerifiedPhoneIsTakenEvenWhenWrittenDifferently() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val local = phoneLocal()
        val owner = client.register(email(), local)
        assertEquals(HttpStatusCode.Created, owner.status)
        // Only a verified number blocks others (an unverified claim must not lock the real owner out).
        PostgresUserRepository(dataSource).markPhoneVerified(owner.body<AuthResponse>().user.id, "+84" + local.drop(1))

        val spaced = "+84 " + local.drop(1).chunked(3).joinToString(" ")
        val again = client.register(email(), spaced)
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals(AuthErrorCodes.PHONE_TAKEN, again.body<ErrorResponse>().code)
    }

    @Test
    fun phoneOtpFlowWorksEndToEndOnPostgres() = testApplication {
        val provider = DevPhoneVerificationProvider()
        val services = AuthServices.create(
            config = AuthConfig(jwtSecret = "x".repeat(40)),
            users = PostgresUserRepository(dataSource),
            hasher = Pbkdf2PasswordHasher(iterations = 1_000),
            verifications = PostgresPhoneVerificationRepository(dataSource),
            otpProvider = provider,
        )
        application { module(services) }
        val client = jsonClient()
        val local = phoneLocal()
        val e164 = "+84" + local.drop(1)
        val registered = client.register(email(), local).body<AuthResponse>()
        val token = registered.accessToken

        val sent = client.post(AuthApi.PHONE_OTP_SEND) { bearerAuth(token) }
        assertEquals(HttpStatusCode.Accepted, sent.status)
        assertEquals("+84••••••" + e164.takeLast(3), sent.body<PhoneOtpSendResponse>().maskedPhone)

        // The cooldown is enforced from the database, not from memory.
        val tooSoon = client.post(AuthApi.PHONE_OTP_SEND) { bearerAuth(token) }
        assertEquals(HttpStatusCode.TooManyRequests, tooSoon.status)
        assertEquals(AuthErrorCodes.TOO_MANY_REQUESTS, tooSoon.body<ErrorResponse>().code)
        assertEquals(true, tooSoon.headers[HttpHeaders.RetryAfter] != null)

        val code = checkNotNull(provider.peekCode(e164))
        val wrong = if (code == "000000") "111111" else "000000"
        val bad = client.post(AuthApi.PHONE_OTP_VERIFY) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PhoneOtpVerifyRequest(wrong))
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals(4, bad.body<ErrorResponse>().attemptsRemaining)

        val good = client.post(AuthApi.PHONE_OTP_VERIFY) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PhoneOtpVerifyRequest(code))
        }
        assertEquals(HttpStatusCode.OK, good.status)
        assertEquals(true, good.body<UserDto>().phoneVerified)
        assertEquals(true, client.get(AuthApi.ME) { bearerAuth(token) }.body<UserDto>().phoneVerified)

        // Verified numbers cannot be verified again, and now block other sign-ups.
        assertEquals(HttpStatusCode.Conflict, client.post(AuthApi.PHONE_OTP_SEND) { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Conflict, client.register(email(), local).status)

        // The audit trail landed in PostgreSQL (and never contains the code).
        val rows = dataSource.connection.use { c ->
            c.prepareStatement("SELECT status, country_code, provider FROM phone_verifications WHERE user_id = ?::uuid").use { ps ->
                ps.setString(1, registered.user.id)
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3))) }
                }
            }
        }
        assertEquals(listOf(Triple("approved", "VN", "dev")), rows)
    }

    @Test
    fun duplicateEmailIsAConflictAndInvalidPhoneIsABadRequest() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val email = email()
        assertEquals(HttpStatusCode.Created, client.register(email, phoneLocal()).status)

        val dup = client.register(email.uppercase(), phoneLocal())
        assertEquals(HttpStatusCode.Conflict, dup.status)
        assertEquals(AuthErrorCodes.EMAIL_TAKEN, dup.body<ErrorResponse>().code)

        assertEquals(HttpStatusCode.BadRequest, client.register(email(), "12345").status)
        assertEquals(HttpStatusCode.BadRequest, client.register(email(), "").status)
    }

    @Test
    fun wrongPasswordIsRejectedAndLoginUpdatesLastLogin() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val email = email()
        client.register(email, phoneLocal())

        assertEquals(HttpStatusCode.Unauthorized, client.login(email, "wrong-password").status)
        assertEquals(HttpStatusCode.OK, client.login(email).status)

        val stamped = dataSource.connection.use { c ->
            c.prepareStatement("SELECT last_login_at IS NOT NULL FROM users WHERE email = ?").use { ps ->
                ps.setString(1, email)
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getBoolean(1)
                }
            }
        }
        assertEquals(true, stamped)
    }
}
