package com.tana.spa.featureflags

import com.tana.spa.auth.AuthConfig
import com.tana.spa.auth.AuthServices
import com.tana.spa.auth.InMemoryUserRepository
import com.tana.spa.auth.PasswordHasher
import com.tana.spa.auth.SeedAccount
import com.tana.spa.auth.UserRole
import com.tana.spa.auth.seedAccounts
import com.tana.spa.module
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.LoginRequest
import com.tana.spa.shared.auth.RegisterRequest
import com.tana.spa.shared.featureflags.ConfigurableFeatureFlags
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlagAdminResponse
import com.tana.spa.shared.featureflags.FeatureFlagApi
import com.tana.spa.shared.featureflags.FeatureFlagWriteRequest
import com.tana.spa.shared.featureflags.FeatureFlags
import com.tana.spa.shared.featureflags.FeatureFlagsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureFlagRoutesTest {

    private class FakeHasher : PasswordHasher {
        override fun hash(password: String) = "fake:$password"
        override fun verify(password: String, stored: String) = stored == "fake:$password"
    }

    private val otp = FeatureFlag.PHONE_OTP_VERIFICATION
    private val admin = SeedAccount("ops@tana.local", "Ops", "admin-password", UserRole.ADMIN)
    private val users = InMemoryUserRepository()
    private val hasher = FakeHasher()

    private fun services(deployDefaults: FeatureFlags = FeatureFlags.defaults): AuthServices {
        seedAccounts(users, hasher, listOf(admin))
        return AuthServices.create(
            config = AuthConfig(jwtSecret = "x".repeat(40)),
            users = users,
            hasher = hasher,
            flagDefaults = deployDefaults,
        )
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient { install(ContentNegotiation) { json() } }

    private suspend fun HttpClient.signIn(email: String, password: String): AuthResponse = post(AuthApi.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(email, password))
    }.body()

    /** An access token for the seeded administrator. */
    private suspend fun HttpClient.adminToken(): String = signIn(admin.email, admin.password).accessToken

    private suspend fun HttpClient.signUp(email: String = "lan@example.com"): AuthResponse = post(AuthApi.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("Lan", email, "0333 433 969", "password123"))
    }.body()

    private suspend fun HttpClient.flags(token: String? = null): Map<String, Boolean> =
        get(FeatureFlagApi.RESOLVED) { token?.let { bearerAuth(it) } }.body<FeatureFlagsResponse>().flags

    private suspend fun HttpClient.setGlobal(key: String, enabled: Boolean, token: String?): HttpResponse =
        put(FeatureFlagApi.global(key)) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(FeatureFlagWriteRequest(enabled))
        }

    private suspend fun HttpClient.setForUser(key: String, userId: String, enabled: Boolean, token: String): HttpResponse =
        put(FeatureFlagApi.forUser(key, userId)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(FeatureFlagWriteRequest(enabled, note = "tester"))
        }

    @Test
    fun anyoneMayReadTheFlagsBecauseTheSignInScreenNeedsThem() = testApplication {
        application { module(services()) }

        val flags = jsonClient().flags()

        assertEquals(FeatureFlag.entries.map { it.key }.toSet(), flags.keys)
        assertEquals(otp.defaultEnabled, flags[otp.key])
    }

    @Test
    fun aSignedInCallerGetsTheirOwnOverrideWhileEverybodyElseDoesNot() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val tester = client.signUp()

        assertEquals(HttpStatusCode.OK, client.setForUser(otp.key, tester.user.id, true, client.adminToken()).status)

        assertEquals(true, client.flags(tester.accessToken)[otp.key], "the override applies to this user")
        assertEquals(otp.defaultEnabled, client.flags()[otp.key], "and to nobody else")
    }

    @Test
    fun aGlobalWriteChangesWhatEverybodySees() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        assertEquals(HttpStatusCode.OK, client.setGlobal(otp.key, enabled = true, token = client.adminToken()).status)

        assertEquals(true, client.flags()[otp.key])
    }

    @Test
    fun clearingAGlobalWriteRestoresTheDeployDefault() = testApplication {
        application { module(services(ConfigurableFeatureFlags(mapOf(otp to true)))) }
        val client = jsonClient()
        val token = client.adminToken()
        client.setGlobal(otp.key, enabled = false, token = token)
        assertEquals(false, client.flags()[otp.key])

        val cleared = client.delete(FeatureFlagApi.global(otp.key)) { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, cleared.status)
        assertEquals(true, client.flags()[otp.key], "the deployment decides again")
    }

    @Test
    fun clearingSomethingThatWasNeverStoredIsNotFound() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        val response = client.delete(FeatureFlagApi.global(otp.key)) { bearerAuth(client.adminToken()) }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun theAdminViewShowsWhatIsStoredAndWhatIsOnlyTheDefault() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val token = client.adminToken()
        val tester = client.signUp()
        client.setForUser(otp.key, tester.user.id, enabled = true, token = token)

        val view = client.get(FeatureFlagApi.ADMIN) { bearerAuth(token) }
            .body<FeatureFlagAdminResponse>().flags.single { it.key == otp.key }

        assertNull(view.globalEnabled, "nothing global was written")
        assertEquals(otp.defaultEnabled, view.deployDefault)
        assertEquals(listOf(tester.user.id), view.overrides.map { it.userId })
        assertEquals("tester", view.overrides.single().note)
        assertTrue(view.description.isNotBlank())
    }

    @Test
    fun withoutATokenTheWriteEndpointsAreUnauthorized() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        val write = client.setGlobal(otp.key, enabled = true, token = null)
        assertEquals(HttpStatusCode.Unauthorized, write.status)
        assertEquals(AuthErrorCodes.UNAUTHORIZED, write.body<ErrorResponse>().code)

        assertEquals(HttpStatusCode.Unauthorized, client.get(FeatureFlagApi.ADMIN).status)
        assertEquals(otp.defaultEnabled, client.flags()[otp.key])
    }

    @Test
    fun aCustomersTokenIsSignedInButNotAllowedToWrite() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val customer = client.signUp()

        val write = client.setGlobal(otp.key, enabled = true, token = customer.accessToken)

        assertEquals(HttpStatusCode.Forbidden, write.status)
        assertEquals(AuthErrorCodes.FORBIDDEN, write.body<ErrorResponse>().code)
        assertEquals(otp.defaultEnabled, client.flags()[otp.key], "no write got through")
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get(FeatureFlagApi.ADMIN) { bearerAuth(customer.accessToken) }.status,
        )
    }

    @Test
    fun aTokenThatDoesNotVerifyIsUnauthorized() = testApplication {
        application { module(services()) }

        val response = jsonClient().setGlobal(otp.key, enabled = true, token = "not-a-jwt")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun aFlagThisServerDoesNotKnowIsNotFoundRatherThanStored() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        val response = client.setGlobal("made_up_flag", enabled = true, token = client.adminToken())

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(AuthErrorCodes.UNKNOWN_FLAG, response.body<ErrorResponse>().code)
        assertFalse(client.flags().containsKey("made_up_flag"))
    }

    @Test
    fun anOverrideForSomebodyWhoIsNotAnAccountIsNotFound() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        val response = client.setForUser(
            otp.key,
            "00000000-0000-0000-0000-000000000000",
            enabled = true,
            token = client.adminToken(),
        )

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun removingAUsersOverrideReturnsThemToTheGlobalState() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val token = client.adminToken()
        val tester = client.signUp()
        client.setForUser(otp.key, tester.user.id, enabled = true, token = token)
        assertEquals(true, client.flags(tester.accessToken)[otp.key])

        val removed = client.delete(FeatureFlagApi.forUser(otp.key, tester.user.id)) { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, removed.status)
        assertEquals(otp.defaultEnabled, client.flags(tester.accessToken)[otp.key])
    }

    @Test
    fun anUnusableSessionIsSimplyAnAnonymousRead() = testApplication {
        application { module(services()) }

        val response = jsonClient().get(FeatureFlagApi.RESOLVED) { bearerAuth("not-a-jwt") }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(otp.defaultEnabled, response.body<FeatureFlagsResponse>().flags[otp.key])
    }
}
