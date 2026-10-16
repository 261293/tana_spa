package com.tana.spa.appupdate

import com.tana.spa.auth.AuthConfig
import com.tana.spa.auth.AuthServices
import com.tana.spa.auth.InMemoryUserRepository
import com.tana.spa.auth.PasswordHasher
import com.tana.spa.auth.SeedAccount
import com.tana.spa.auth.UserRole
import com.tana.spa.auth.seedAccounts
import com.tana.spa.module
import com.tana.spa.shared.appupdate.AppUpdateApi
import com.tana.spa.shared.appupdate.AppUpdateConfigResponse
import com.tana.spa.shared.appupdate.AppUpdateConfigWriteRequest
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.LoginRequest
import com.tana.spa.shared.auth.RegisterRequest
import com.tana.spa.shared.featureflags.ConfigurableFeatureFlags
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlags
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
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

class AppUpdateRoutesTest {

    private class FakeHasher : PasswordHasher {
        override fun hash(password: String) = "fake:$password"
        override fun verify(password: String, stored: String) = stored == "fake:$password"
    }

    private val admin = SeedAccount("ops@tana.local", "Ops", "admin-password", UserRole.ADMIN)
    private val users = InMemoryUserRepository()
    private val hasher = FakeHasher()

    private fun services(
        stored: AppUpdateConfigRow? = AppUpdateConfigRow("1.0.0", "2.0.0", forceUpdate = false),
        flagDefaults: FeatureFlags = FeatureFlags.defaults,
    ): AuthServices {
        seedAccounts(users, hasher, listOf(admin))
        return AuthServices.create(
            config = AuthConfig(jwtSecret = "x".repeat(40)),
            users = users,
            hasher = hasher,
            flagDefaults = flagDefaults,
            appUpdateRepository = InMemoryAppUpdateRepository(stored),
        )
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient { install(ContentNegotiation) { json() } }

    private suspend fun HttpClient.signIn(email: String, password: String): AuthResponse = post(AuthApi.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(email, password))
    }.body()

    private suspend fun HttpClient.adminToken(): String = signIn(admin.email, admin.password).accessToken

    private suspend fun HttpClient.customerToken(): String = post(AuthApi.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("Lan", "lan@example.com", "0333 433 969", "password123"))
    }.body<AuthResponse>().accessToken

    private suspend fun HttpClient.readConfig(): HttpResponse = get(AppUpdateApi.CONFIG)

    private suspend fun HttpClient.writeConfig(
        minimum: String,
        latest: String,
        force: Boolean = false,
        token: String? = null,
    ): HttpResponse = put(AppUpdateApi.CONFIG) {
        token?.let { bearerAuth(it) }
        contentType(ContentType.Application.Json)
        setBody(AppUpdateConfigWriteRequest(minimum, latest, force))
    }

    @Test
    fun anyoneMayReadTheConfigBecauseTheGateDecidesBeforeSignIn() = testApplication {
        application { module(services()) }

        val response = jsonClient().readConfig()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(AppUpdateConfigResponse("1.0.0", "2.0.0", false), response.body())
    }

    @Test
    fun nothingStoredAnswersNoContentSoTheClientShowsNoPrompt() = testApplication {
        application { module(services(stored = null)) }

        assertEquals(HttpStatusCode.NoContent, jsonClient().readConfig().status)
    }

    @Test
    fun theKillSwitchTakesTheEndpointOutOfService() = testApplication {
        val off = ConfigurableFeatureFlags(mapOf(FeatureFlag.APP_UPDATE_GATE to false))
        application { module(services(flagDefaults = off)) }

        val response = jsonClient().readConfig()

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(AuthErrorCodes.FEATURE_DISABLED, response.body<ErrorResponse>().code)
    }

    @Test
    fun anAnonymousCallerMayNotWrite() = testApplication {
        application { module(services()) }

        assertEquals(HttpStatusCode.Unauthorized, jsonClient().writeConfig("1.0.0", "2.0.0").status)
    }

    @Test
    fun aCustomersTokenIsSignedInButNotAllowedToWrite() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        val response = client.writeConfig("1.0.0", "2.0.0", token = client.customerToken())

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun anAdminMayRaiseTheFloorAndTheReadReflectsItAtOnce() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        val written = client.writeConfig("2.0.0", "2.1.0", force = true, token = client.adminToken())

        assertEquals(HttpStatusCode.OK, written.status)
        assertEquals(AppUpdateConfigResponse("2.0.0", "2.1.0", true), client.readConfig().body())
    }

    @Test
    fun aTwoPartVersionIsStoredNormalisedBecauseTheColumnRequiresThreeSegments() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        client.writeConfig("1.2", "1.3", token = client.adminToken())

        assertEquals(AppUpdateConfigResponse("1.2.0", "1.3.0", false), client.readConfig().body())
    }

    @Test
    fun aVersionThatIsNotNumbersIsRefused() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        val response = client.writeConfig("nightly", "2.0.0", token = client.adminToken())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(AuthErrorCodes.INVALID_REQUEST, response.body<ErrorResponse>().code)
    }

    @Test
    fun aFloorNewerThanTheLatestReleaseIsRefusedBecauseItWouldStrandEveryone() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        val response = client.writeConfig("3.0.0", "2.0.0", token = client.adminToken())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(AuthErrorCodes.INVALID_REQUEST, response.body<ErrorResponse>().code)
    }
}
