package com.tana.spa.admin

import com.tana.spa.auth.AuthConfig
import com.tana.spa.auth.AuthServices
import com.tana.spa.auth.Pbkdf2PasswordHasher
import com.tana.spa.auth.PostgresUserRepository
import com.tana.spa.auth.SeedAccount
import com.tana.spa.auth.UserRole
import com.tana.spa.auth.seedAccounts
import com.tana.spa.db.TestDatabase
import com.tana.spa.module
import com.tana.spa.notifications.PostgresNotificationRepository
import com.tana.spa.shared.auth.AccountErrorCodes
import com.tana.spa.shared.auth.AccountsApi
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ChangeRoleRequest
import com.tana.spa.shared.auth.CreateAccountRequest
import com.tana.spa.shared.auth.CreatedAccountDto
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.LoginRequest
import com.tana.spa.shared.auth.RegisterRequest
import com.tana.spa.shared.auth.SpaRoles
import com.tana.spa.shared.auth.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountRoutesTest {

    private val dataSource = TestDatabase.dataSource
    private val users = PostgresUserRepository(dataSource)
    private val hasher = Pbkdf2PasswordHasher(iterations = 1_000)

    private val owner = SeedAccount("owner-accounts@tana.local", "Jade Bathhouse", "owner-password", UserRole.OWNER)
    private val admin = SeedAccount("admin-accounts@tana.local", "Ops", "admin-password", UserRole.ADMIN)
    private val staff = SeedAccount("staff-accounts@tana.local", "Linh", "staff-password", UserRole.STAFF)

    private val auth: AuthServices by lazy {
        AuthServices.create(
            config = AuthConfig(jwtSecret = "x".repeat(40)),
            users = users,
            hasher = hasher,
            notificationRepository = PostgresNotificationRepository(dataSource),
        ).also { seedAccounts(users, hasher, listOf(owner, admin, staff)) }
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient { install(ContentNegotiation) { json() } }

    private suspend fun HttpClient.signIn(account: SeedAccount): String = post(AuthApi.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(account.email, account.password))
    }.body<AuthResponse>().accessToken

    private suspend fun HttpClient.signUpGuest(): String {
        val email = "g${UUID.randomUUID().toString().take(10)}@example.com"
        val phone = "039" + (1_000_000 + (SEQ.getAndIncrement() % 9_000_000)).toString()
        return post(AuthApi.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("Hannah Pham", email, phone, "password123"))
        }.body<AuthResponse>().accessToken
    }

    private suspend fun HttpClient.createAccount(token: String, role: String): HttpResponse = post(AccountsApi.BASE) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(
            CreateAccountRequest(
                displayName = "New Person",
                email = "n${UUID.randomUUID().toString().take(10)}@tana.local",
                password = "password123",
                role = role,
            ),
        )
    }

    private suspend fun HttpClient.changeRole(token: String, accountId: String, role: String): HttpResponse =
        patch(AccountsApi.role(accountId)) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ChangeRoleRequest(role))
        }

    private suspend fun HttpClient.newAccount(token: String, role: String): CreatedAccountDto = createAccount(token, role).body()

    private suspend fun HttpClient.meId(token: String): String = get(AuthApi.ME) { bearerAuth(token) }.body<UserDto>().id

    @Test
    fun the_owner_takes_on_staff() = testApplication {
        application { module(auth) }
        val client = jsonClient()

        val response = client.createAccount(client.signIn(owner), SpaRoles.STAFF)

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(SpaRoles.STAFF, response.body<CreatedAccountDto>().role)
    }

    @Test
    fun the_owner_cannot_mint_another_owner() = testApplication {
        application { module(auth) }
        val client = jsonClient()

        val response = client.createAccount(client.signIn(owner), SpaRoles.OWNER)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(AccountErrorCodes.ROLE_NOT_ALLOWED, response.body<ErrorResponse>().code)
    }

    @Test
    fun the_owner_cannot_make_an_administrator_either() = testApplication {
        application { module(auth) }
        val client = jsonClient()

        val response = client.createAccount(client.signIn(owner), SpaRoles.ADMIN)

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun an_administrator_may_make_an_owner() = testApplication {
        application { module(auth) }
        val client = jsonClient()

        val response = client.createAccount(client.signIn(admin), SpaRoles.OWNER)

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(SpaRoles.OWNER, response.body<CreatedAccountDto>().role)
    }

    @Test
    fun staff_and_guests_cannot_create_anyone() = testApplication {
        application { module(auth) }
        val client = jsonClient()

        for (token in listOf(client.signIn(staff), client.signUpGuest())) {
            assertEquals(HttpStatusCode.Forbidden, client.createAccount(token, SpaRoles.CUSTOMER).status)
        }
    }

    @Test
    fun an_address_that_already_has_an_account_is_refused_rather_than_taken_over() = testApplication {
        application { module(auth) }
        val client = jsonClient()
        val token = client.signIn(admin)

        val response = client.post(AccountsApi.BASE) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CreateAccountRequest("Someone Else", owner.email, "password123", SpaRoles.STAFF))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(AuthErrorCodes.EMAIL_TAKEN, response.body<ErrorResponse>().code)
        assertEquals(UserRole.OWNER, users.findByEmail(owner.email)?.role)
    }

    @Test
    fun the_owner_promotes_a_customer_to_staff() = testApplication {
        application { module(auth) }
        val client = jsonClient()
        val token = client.signIn(owner)
        val person = client.newAccount(token, SpaRoles.CUSTOMER)

        val response = client.changeRole(token, person.id, SpaRoles.STAFF)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(SpaRoles.STAFF, response.body<CreatedAccountDto>().role)
        assertEquals(UserRole.STAFF, users.findById(person.id)?.role)
    }

    @Test
    fun the_owner_puts_staff_back_to_customer() = testApplication {
        application { module(auth) }
        val client = jsonClient()
        val token = client.signIn(owner)
        val person = client.newAccount(token, SpaRoles.STAFF)

        assertEquals(HttpStatusCode.OK, client.changeRole(token, person.id, SpaRoles.CUSTOMER).status)
        assertEquals(UserRole.CUSTOMER, users.findById(person.id)?.role)
    }

    @Test
    fun the_owner_cannot_promote_anybody_to_owner() = testApplication {
        application { module(auth) }
        val client = jsonClient()
        val token = client.signIn(owner)
        val person = client.newAccount(token, SpaRoles.STAFF)

        val response = client.changeRole(token, person.id, SpaRoles.OWNER)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(AccountErrorCodes.ROLE_NOT_ALLOWED, response.body<ErrorResponse>().code)
        assertEquals(UserRole.STAFF, users.findById(person.id)?.role)
    }

    @Test
    fun the_owner_cannot_demote_an_administrator() = testApplication {
        application { module(auth) }
        val client = jsonClient()
        val adminId = client.meId(client.signIn(admin))

        val response = client.changeRole(client.signIn(owner), adminId, SpaRoles.CUSTOMER)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(UserRole.ADMIN, users.findById(adminId)?.role)
    }

    @Test
    fun no_account_may_change_its_own_role() = testApplication {
        application { module(auth) }
        val client = jsonClient()
        val token = client.signIn(admin)
        val adminId = client.meId(token)

        val response = client.changeRole(token, adminId, SpaRoles.CUSTOMER)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(AccountErrorCodes.OWN_ROLE_UNCHANGEABLE, response.body<ErrorResponse>().code)
        assertEquals(UserRole.ADMIN, users.findById(adminId)?.role)
    }

    @Test
    fun an_administrator_promotes_a_customer_all_the_way_to_owner() = testApplication {
        application { module(auth) }
        val client = jsonClient()
        val token = client.signIn(admin)
        val person = client.newAccount(token, SpaRoles.CUSTOMER)

        assertEquals(HttpStatusCode.OK, client.changeRole(token, person.id, SpaRoles.OWNER).status)
        assertEquals(UserRole.OWNER, users.findById(person.id)?.role)
    }

    @Test
    fun staff_and_guests_cannot_change_anybody() = testApplication {
        application { module(auth) }
        val client = jsonClient()
        val person = client.newAccount(client.signIn(owner), SpaRoles.CUSTOMER)

        for (token in listOf(client.signIn(staff), client.signUpGuest())) {
            assertEquals(HttpStatusCode.Forbidden, client.changeRole(token, person.id, SpaRoles.STAFF).status)
        }
        assertEquals(UserRole.CUSTOMER, users.findById(person.id)?.role)
    }

    @Test
    fun an_account_that_does_not_exist_is_a_not_found() = testApplication {
        application { module(auth) }
        val client = jsonClient()

        val response = client.changeRole(client.signIn(admin), UUID.randomUUID().toString(), SpaRoles.STAFF)

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private companion object {
        val SEQ = AtomicInteger((System.nanoTime() % 5_000_000L).toInt())
    }
}
