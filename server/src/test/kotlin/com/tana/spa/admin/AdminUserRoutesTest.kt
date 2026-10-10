package com.tana.spa.admin

import com.tana.spa.auth.AuthConfig
import com.tana.spa.auth.AuthServices
import com.tana.spa.auth.InMemoryUserRepository
import com.tana.spa.auth.PasswordHasher
import com.tana.spa.auth.SeedAdmin
import com.tana.spa.auth.seedAdmins
import com.tana.spa.module
import com.tana.spa.shared.admin.AdminApi
import com.tana.spa.shared.admin.AdminUsersResponse
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.LoginRequest
import com.tana.spa.shared.auth.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminUserRoutesTest {

    private class FakeHasher : PasswordHasher {
        override fun hash(password: String) = "fake:$password"
        override fun verify(password: String, stored: String) = stored == "fake:$password"
    }

    private val admin = SeedAdmin("ops@tana.local", "Ops", "admin-password")
    private val users = InMemoryUserRepository()
    private val hasher = FakeHasher()

    private fun services(): AuthServices {
        seedAdmins(users, hasher, listOf(admin))
        return AuthServices.create(
            config = AuthConfig(jwtSecret = "x".repeat(40)),
            users = users,
            hasher = hasher,
        )
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient { install(ContentNegotiation) { json() } }

    private suspend fun HttpClient.adminToken(): String = post(AuthApi.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(admin.email, admin.password))
    }.body<AuthResponse>().accessToken

    private suspend fun HttpClient.signUp(name: String, email: String, phone: String): AuthResponse = post(AuthApi.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(name, email, phone, "password123"))
    }.body()

    private suspend fun HttpClient.search(query: String, token: String, limit: Int? = null): AdminUsersResponse =
        get(AdminApi.USERS + "?q=" + query + (limit?.let { "&limit=$it" } ?: "")) { bearerAuth(token) }.body()

    @Test
    fun aUserIsFoundByNameByEmailAndById() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val token = client.adminToken()
        val lan = client.signUp("Lan Nguyen", "lan@example.com", "0333 433 969")

        assertEquals(listOf(lan.user.id), client.search("Lan", token).users.map { it.id })
        assertEquals(listOf(lan.user.id), client.search("lan@example.com", token).users.map { it.id })
        assertEquals(listOf(lan.user.id), client.search(lan.user.id, token).users.map { it.id })
    }

    @Test
    fun theAnswerCarriesWhatIsNeededToTellTwoPeopleApart() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val token = client.adminToken()
        client.signUp("Lan Nguyen", "lan1@example.com", "0333 433 969")
        client.signUp("Lan Nguyen", "lan2@example.com", "0333 433 970")

        val found = client.search("Lan", token).users

        assertEquals(2, found.size, "two real people can share a name")
        assertEquals(listOf("lan1@example.com", "lan2@example.com"), found.map { it.email }.sorted())
        assertTrue(found.all { it.role == "customer" })
        assertTrue(found.all { it.id.isNotBlank() })
    }

    @Test
    fun anEmptySearchBoxIsNotARequestForEveryCustomer() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val token = client.adminToken()
        client.signUp("Lan Nguyen", "lan@example.com", "0333 433 969")

        assertTrue(client.search("", token).users.isEmpty())
        assertTrue(client.search("%20", token).users.isEmpty())
    }

    @Test
    fun theLimitIsHonouredAndClamped() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val token = client.adminToken()
        repeat(3) { client.signUp("Lan Nguyen", "lan$it@example.com", "033343396$it") }

        assertEquals(2, client.search("Lan", token, limit = 2).users.size)
        assertEquals(3, client.search("Lan", token, limit = AdminApi.MAX_USER_RESULTS * 10).users.size)
    }

    @Test
    fun onlyAnAdministratorMaySearch() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val customer = client.signUp("Lan Nguyen", "lan@example.com", "0333 433 969")

        assertEquals(HttpStatusCode.Unauthorized, client.get(AdminApi.USERS + "?q=Lan").status)

        val forbidden = client.get(AdminApi.USERS + "?q=Lan") { bearerAuth(customer.accessToken) }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertEquals(AuthErrorCodes.FORBIDDEN, forbidden.body<ErrorResponse>().code)
    }

    @Test
    fun thePortalPageItselfIsServed() = testApplication {
        application { module(services()) }

        val page = jsonClient().get("/admin/index.html")

        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.body<String>().contains("Feature flags"), "the portal page should be on the classpath")
    }
}
