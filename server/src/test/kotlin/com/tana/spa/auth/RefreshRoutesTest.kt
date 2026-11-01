package com.tana.spa.auth

import com.tana.spa.module
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.LoginRequest
import com.tana.spa.shared.auth.LogoutRequest
import com.tana.spa.shared.auth.RefreshRequest
import com.tana.spa.shared.auth.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The refresh HTTP contract: what a client may spend, what it gets back, and what ends a session. */
class RefreshRoutesTest {

    private class FakeHasher : PasswordHasher {
        override fun hash(password: String) = "fake:$password"
        override fun verify(password: String, stored: String) = stored == "fake:$password"
    }

    private fun services() = AuthServices.create(
        config = AuthConfig(jwtSecret = "x".repeat(40)),
        hasher = FakeHasher(),
    )

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient { install(ContentNegotiation) { json() } }

    private suspend fun HttpClient.signUp(email: String = "lan@example.com"): AuthResponse = post(AuthApi.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("Lan", email, "0333 433 969", "password123"))
    }.body()

    private suspend fun HttpClient.refresh(token: String): HttpResponse = post(AuthApi.REFRESH) {
        contentType(ContentType.Application.Json)
        setBody(RefreshRequest(token))
    }

    private suspend fun HttpClient.logout(token: String): HttpResponse = post(AuthApi.LOGOUT) {
        contentType(ContentType.Application.Json)
        setBody(LogoutRequest(token))
    }

    private suspend fun HttpClient.me(token: String): HttpResponse = get(AuthApi.ME) { bearerAuth(token) }

    @Test
    fun signing_up_and_signing_in_both_hand_out_a_refresh_token() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        val registered = client.signUp()
        assertTrue(registered.refreshToken.isNotEmpty())
        assertTrue(registered.refreshExpiresInSeconds > registered.expiresInSeconds)

        val signedIn: AuthResponse = client.post(AuthApi.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("lan@example.com", "password123"))
        }.body()
        assertTrue(signedIn.refreshToken.isNotEmpty())
        assertNotEquals(registered.refreshToken, signedIn.refreshToken)
    }

    @Test
    fun spending_a_refresh_token_returns_a_working_access_token_and_a_new_refresh_token() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val first = client.signUp()

        val response = client.refresh(first.refreshToken)
        assertEquals(HttpStatusCode.OK, response.status)

        val refreshed: AuthResponse = response.body()
        assertNotEquals(first.refreshToken, refreshed.refreshToken)
        assertEquals(first.user.id, refreshed.user.id)
        assertEquals(HttpStatusCode.OK, client.me(refreshed.accessToken).status)
    }

    @Test
    fun a_spent_refresh_token_is_refused_and_takes_its_replacement_with_it() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val first = client.signUp()
        val second: AuthResponse = client.refresh(first.refreshToken).body()

        val replayed = client.refresh(first.refreshToken)
        assertEquals(HttpStatusCode.Unauthorized, replayed.status)
        assertEquals(AuthErrorCodes.INVALID_REFRESH_TOKEN, replayed.body<ErrorResponse>().code)

        assertEquals(HttpStatusCode.Unauthorized, client.refresh(second.refreshToken).status)
    }

    @Test
    fun an_unknown_refresh_token_is_refused_the_same_way_a_replayed_one_is() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        val response = client.refresh("never-issued")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(AuthErrorCodes.INVALID_REFRESH_TOKEN, response.body<ErrorResponse>().code)
    }

    @Test
    fun signing_out_ends_the_session_for_good() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val auth = client.signUp()

        assertEquals(HttpStatusCode.NoContent, client.logout(auth.refreshToken).status)

        assertEquals(HttpStatusCode.Unauthorized, client.refresh(auth.refreshToken).status)
    }

    @Test
    fun signing_out_needs_no_access_token_because_the_one_it_ends_may_have_expired() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val auth = client.signUp()

        val response = client.logout(auth.refreshToken)

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun signing_out_with_an_unknown_token_says_nothing_about_whether_it_existed() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        assertEquals(HttpStatusCode.NoContent, client.logout("never-issued").status)
    }

    @Test
    fun one_devices_sign_out_leaves_another_device_signed_in() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val phone = client.signUp()
        val laptop: AuthResponse = client.post(AuthApi.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("lan@example.com", "password123"))
        }.body()

        client.logout(phone.refreshToken)

        assertEquals(HttpStatusCode.OK, client.refresh(laptop.refreshToken).status)
    }
}
