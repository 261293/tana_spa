package com.tana.spa.feature.auth.data.remote

import com.tana.spa.core.network.NetworkClient
import com.tana.spa.core.network.SessionRefresher
import com.tana.spa.core.network.withDefaults
import com.tana.spa.feature.auth.FakeSessionStore
import com.tana.spa.feature.auth.TestFixtures
import com.tana.spa.feature.auth.data.SessionRefreshCoordinator
import com.tana.spa.feature.auth.data.remote.source.AuthRemoteApiImpl
import com.tana.spa.feature.auth.data.repository.AuthRepositoryImpl
import com.tana.spa.feature.auth.domain.model.OtpSendResult
import com.tana.spa.shared.Constants
import com.tana.spa.shared.auth.AuthErrorCodes
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
import kotlin.test.assertTrue

private data class Sent(val method: HttpMethod, val target: String, val authorization: String?, val apikey: String?, val body: String)

private class Reply(val status: HttpStatusCode, val body: String)

class AuthRemoteApiImplTest {

    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val sent = mutableListOf<Sent>()

    private fun grant(access: String, refresh: String) =
        """{"access_token":"$access","token_type":"bearer","expires_in":3600,"refresh_token":"$refresh","user":{"id":"u1"}}"""

    private val me =
        """{"id":"u1","role":"customer","email":"lan@example.com","phone":"+84912345678","displayName":"Lan","phoneVerified":false}"""

    private fun client(refresher: () -> SessionRefresher = { SessionRefresher.none }, route: (Sent) -> Reply): NetworkClient {
        val engine = MockEngine { request ->
            val target = request.url.encodedPath + (request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
            val call = Sent(
                request.method,
                target,
                request.headers[HttpHeaders.Authorization],
                request.headers["apikey"],
                request.body.toByteArray().decodeToString(),
            )
            sent += call
            val reply = route(call)
            respond(reply.body, reply.status, json)
        }
        return NetworkClient(HttpClient(engine).withDefaults(refresher))
    }

    private fun api(route: (Sent) -> Reply) = AuthRemoteApiImpl(client(route = route), "http://api.test")

    private fun ok(body: String) = Reply(HttpStatusCode.OK, body)

    @Test
    fun login_posts_password_grant_then_me() = runTest {
        val api = api { if (it.target.startsWith("/auth/v1/token")) ok(grant("a1", "r1")) else ok(me) }

        val response = api.login("lan@example.com", "password123")

        assertEquals(listOf("/auth/v1/token?grant_type=password", "/rest/v1/rpc/me"), sent.map { it.target })
        assertEquals("""{"email":"lan@example.com","password":"password123"}""", sent[0].body)
        assertEquals("Bearer a1", sent[1].authorization)
        assertEquals("a1", response.accessToken)
        assertEquals("r1", response.refreshToken)
        assertEquals(3600, response.expiresInSeconds)
        assertEquals(30L * 24 * 60 * 60, response.refreshExpiresInSeconds)
        assertEquals("Lan", response.user.displayName)
    }

    @Test
    fun register_sends_full_name_and_phone_metadata() = runTest {
        val api = api { if (it.target.startsWith("/auth/v1/signup")) ok(grant("a1", "r1")) else ok(me) }

        val response = api.register("Phương Anh", "lan@example.com", "0912 345 678", "password123")

        assertEquals(listOf("/auth/v1/signup", "/rest/v1/rpc/me"), sent.map { it.target })
        assertEquals(
            """{"email":"lan@example.com","password":"password123","data":{"full_name":"Phương Anh","phone":"+84912345678"}}""",
            sent[0].body,
        )
        assertEquals("+84912345678", response.user.phone)
    }

    @Test
    fun email_exists_maps_to_email_taken() = runTest {
        listOf("email_exists", "user_already_exists").forEach { authCode ->
            val api =
                api {
                    Reply(HttpStatusCode.UnprocessableEntity, """{"code":422,"error_code":"$authCode","msg":"User already registered"}""")
                }

            val e = assertFailsWith<AuthApiException> { api.register("Lan", "lan@example.com", "0912345678", "password123") }

            assertEquals(422, e.status)
            assertEquals(AuthErrorCodes.EMAIL_TAKEN, e.code)
        }
    }

    @Test
    fun wrong_password_maps_to_invalid_credentials() = runTest {
        val api =
            api {
                Reply(HttpStatusCode.BadRequest, """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}""")
            }

        val e = assertFailsWith<AuthApiException> { api.login("lan@example.com", "nope") }

        assertEquals(AuthErrorCodes.INVALID_CREDENTIALS, e.code)
    }

    @Test
    fun spent_refresh_token_maps_to_invalid_refresh_token() = runTest {
        val api = api { Reply(HttpStatusCode.BadRequest, """{"code":400,"error_code":"refresh_token_not_found","msg":"x"}""") }

        val e = assertFailsWith<AuthApiException> { api.refresh("r0") }

        assertEquals(AuthErrorCodes.INVALID_REFRESH_TOKEN, e.code)
        assertEquals("""{"refresh_token":"r0"}""", sent.single().body)
        assertEquals("/auth/v1/token?grant_type=refresh_token", sent.single().target)
    }

    @Test
    fun rpc_error_codes_pass_through() = runTest {
        val api =
            api { Reply(HttpStatusCode.ServiceUnavailable, """{"code":"feature_disabled","message":"off","details":null,"hint":null}""") }

        val e = assertFailsWith<AuthApiException> { api.sendPhoneOtp("a1") }

        assertEquals(503, e.status)
        assertEquals(AuthErrorCodes.FEATURE_DISABLED, e.code)
    }

    @Test
    fun unreadable_error_body_still_reports_status() = runTest {
        val api = api { Reply(HttpStatusCode.BadGateway, "<html>Bad gateway</html>") }

        val e = assertFailsWith<AuthApiException> { api.login("lan@example.com", "pw") }

        assertEquals(502, e.status)
        assertEquals(null, e.code)
    }

    @Test
    fun every_request_carries_apikey() = runTest {
        val api = api { if (it.target.startsWith("/auth/v1/token")) ok(grant("a1", "r1")) else ok(me) }

        api.login("lan@example.com", "password123")
        api.logout("a1")

        assertEquals(3, sent.size)
        sent.forEach { assertEquals(Constants.SUPABASE_PUBLISHABLE_KEY, it.apikey, it.target) }
    }

    @Test
    fun logout_ends_only_this_session_with_its_access_token() = runTest {
        val api = api { Reply(HttpStatusCode.NoContent, "") }

        api.logout("a1")

        assertEquals("/auth/v1/logout?scope=local", sent.single().target)
        assertEquals("Bearer a1", sent.single().authorization)
    }

    @Test
    fun send_otp_asks_auth_to_text_the_number_on_the_account() = runTest {
        val api = api { if (it.target == "/rest/v1/rpc/me") ok(me) else ok("""{"id":"u1"}""") }

        val result = api.sendPhoneOtp("a1")

        assertEquals(listOf("/rest/v1/rpc/me", "/auth/v1/user"), sent.map { it.target })
        assertEquals(HttpMethod.Put, sent[1].method)
        assertEquals("Bearer a1", sent[1].authorization)
        assertEquals("""{"phone":"+84912345678"}""", sent[1].body)
        assertEquals(60, result.resendAfterSeconds)
        assertEquals(3600, result.expiresInSeconds)
        assertEquals("+84••••••678", result.maskedPhone)
    }

    @Test
    fun send_otp_for_a_verified_phone_reports_already_verified() = runTest {
        val api = api { ok(me.replace("\"phoneVerified\":false", "\"phoneVerified\":true")) }

        val e = assertFailsWith<AuthApiException> { api.sendPhoneOtp("a1") }

        assertEquals(AuthErrorCodes.PHONE_ALREADY_VERIFIED, e.code)
        assertEquals(1, sent.size)
    }

    @Test
    fun phone_exists_on_otp_maps_to_phone_claimed() = runTest {
        val phoneExists = Reply(HttpStatusCode.UnprocessableEntity, """{"code":422,"error_code":"phone_exists"}""")
        val api = api { if (it.target == "/rest/v1/rpc/me") ok(me) else phoneExists }

        val e = assertFailsWith<AuthApiException> { api.sendPhoneOtp("a1") }

        assertEquals(AuthErrorCodes.PHONE_CLAIMED, e.code)
    }

    @Test
    fun verify_otp_posts_phone_change_then_reads_me() = runTest {
        val api = api { if (it.target == "/auth/v1/verify") ok(grant("a2", "r2")) else ok(me) }

        api.verifyPhoneOtp("a1", "123456")

        assertEquals(listOf("/rest/v1/rpc/me", "/auth/v1/verify", "/rest/v1/rpc/me"), sent.map { it.target })
        assertEquals("""{"type":"phone_change","phone":"+84912345678","token":"123456"}""", sent[1].body)
    }

    @Test
    fun expired_token_on_rpc_refreshes_once() = runTest {
        val store = FakeSessionStore(TestFixtures.session.copy(accessToken = "stale", refreshToken = "r0"))
        lateinit var coordinator: SessionRefreshCoordinator
        val network = client(refresher = { coordinator }) {
            when {
                it.target == "/rest/v1/rpc/me" && it.authorization == "Bearer stale" ->
                    Reply(HttpStatusCode.Unauthorized, """{"code":"PGRST303","message":"JWT expired"}""")

                it.target == "/auth/v1/token?grant_type=refresh_token" -> ok(grant("fresh", "r1"))

                it.target == "/rest/v1/rpc/me" -> ok(me)

                else -> ok("""{"id":"u1"}""")
            }
        }
        val repository = AuthRepositoryImpl(AuthRemoteApiImpl(network, "http://api.test"), store, nowMillis = { 0L })
        coordinator = SessionRefreshCoordinator(repository)

        val result = repository.sendPhoneOtp()

        assertEquals(
            listOf("/rest/v1/rpc/me", "/auth/v1/token?grant_type=refresh_token", "/rest/v1/rpc/me", "/rest/v1/rpc/me", "/auth/v1/user"),
            sent.map { it.target },
        )
        assertEquals(listOf("Bearer stale", null, "Bearer fresh", "Bearer fresh"), sent.take(4).map { it.authorization })
        assertEquals(1, sent.count { it.target.startsWith("/auth/v1/token") })
        assertEquals("fresh", store.stored?.accessToken)
        assertTrue(result is OtpSendResult.Sent)
    }
}
