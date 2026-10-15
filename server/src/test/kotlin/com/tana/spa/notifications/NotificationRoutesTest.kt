package com.tana.spa.notifications

import com.tana.spa.auth.AuthConfig
import com.tana.spa.auth.AuthServices
import com.tana.spa.auth.InMemoryUserRepository
import com.tana.spa.auth.PasswordHasher
import com.tana.spa.auth.SeedAccount
import com.tana.spa.auth.UserRole
import com.tana.spa.auth.seedAccounts
import com.tana.spa.module
import com.tana.spa.notifications.push.PushMessage
import com.tana.spa.notifications.push.PushOutcome
import com.tana.spa.notifications.push.PushSender
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.LoginRequest
import com.tana.spa.shared.auth.RegisterRequest
import com.tana.spa.shared.featureflags.ConfigurableFeatureFlags
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlags
import com.tana.spa.shared.notifications.DeviceRegistrationRequest
import com.tana.spa.shared.notifications.DeviceUnregisterRequest
import com.tana.spa.shared.notifications.NotificationApi
import com.tana.spa.shared.notifications.NotificationListResponse
import com.tana.spa.shared.notifications.NotificationSendRequest
import com.tana.spa.shared.notifications.NotificationSendResponse
import com.tana.spa.shared.notifications.NotificationType
import com.tana.spa.shared.notifications.UnreadCountResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationRoutesTest {

    private class FakeHasher : PasswordHasher {
        override fun hash(password: String) = "fake:$password"
        override fun verify(password: String, stored: String) = stored == "fake:$password"
    }

    /** Records what it was asked to send and answers with whatever the test needs. */
    private class RecordingPushSender(private val outcome: (String) -> PushOutcome = { PushOutcome.Delivered(it) }) : PushSender {
        override val name = "recording"
        val sent = mutableListOf<Pair<List<String>, PushMessage>>()

        override fun send(tokens: List<String>, message: PushMessage): List<PushOutcome> {
            sent += tokens to message
            return tokens.map(outcome)
        }
    }

    private val admin = SeedAccount("ops@tana.local", "Ops", "admin-password", UserRole.ADMIN)
    private lateinit var built: AuthServices
    private val deviceToken = "fcm-token-for-the-first-device"

    private val notifications = InMemoryNotificationRepository()
    private var push = RecordingPushSender()

    private fun services(flagDefaults: FeatureFlags = ConfigurableFeatureFlags(mapOf(FeatureFlag.NOTIFICATIONS to true))): AuthServices {
        val users = InMemoryUserRepository()
        val hasher = FakeHasher()
        seedAccounts(users, hasher, listOf(admin))
        built = AuthServices.create(
            config = AuthConfig(jwtSecret = "x".repeat(40)),
            users = users,
            hasher = hasher,
            flagDefaults = flagDefaults,
            notificationRepository = notifications,
            pushSender = push,
        )
        return built
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient { install(ContentNegotiation) { json() } }

    private suspend fun HttpClient.adminToken(): String = post(AuthApi.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(admin.email, admin.password))
    }.body<AuthResponse>().accessToken

    private suspend fun HttpClient.signUp(email: String = "lan@example.com", phone: String = "0333 433 969"): AuthResponse =
        post(AuthApi.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("Lan", email, phone, "password123"))
        }.body()

    private suspend fun HttpClient.list(token: String, limit: Int? = null, before: Long? = null): HttpResponse = get(NotificationApi.LIST) {
        bearerAuth(token)
        limit?.let { parameter(NotificationApi.LIMIT_QUERY, it) }
        before?.let { parameter(NotificationApi.BEFORE_QUERY, it) }
    }

    private suspend fun HttpClient.registerDevice(token: String, deviceToken: String, platform: String = "android"): HttpResponse =
        post(NotificationApi.DEVICES) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(DeviceRegistrationRequest(token = deviceToken, platform = platform, appVersion = "1.0.0"))
        }

    private suspend fun HttpClient.send(
        adminToken: String,
        userId: String,
        title: String = "Đơn hàng đã xác nhận",
        body: String? = "Đơn #1234 đang được chuẩn bị",
        type: String = NotificationType.ORDER.wire,
        data: Map<String, String> = mapOf("orderId" to "1234"),
    ): HttpResponse = post(NotificationApi.SEND) {
        bearerAuth(adminToken)
        contentType(ContentType.Application.Json)
        setBody(NotificationSendRequest(userId = userId, type = type, title = title, body = body, data = data))
    }

    @Test
    fun anAnonymousCallerSeesNothing() = testApplication {
        application { module(services()) }

        assertEquals(HttpStatusCode.Unauthorized, jsonClient().get(NotificationApi.LIST).status)
    }

    @Test
    fun theFlagTakesEveryReadOutOfService() = testApplication {
        application { module(services(flagDefaults = FeatureFlags.defaults)) }
        val client = jsonClient()
        val session = client.signUp()

        val response = client.list(session.accessToken)

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(AuthErrorCodes.FEATURE_DISABLED, response.body<ErrorResponse>().code)
    }

    @Test
    fun theFlagShipsSwitchedOffSoTheFeatureIsInertByDefault() = testApplication {
        application { module(services(flagDefaults = FeatureFlags.defaults)) }
        val client = jsonClient()
        val session = client.signUp()

        assertEquals(HttpStatusCode.ServiceUnavailable, client.registerDevice(session.accessToken, deviceToken).status)
    }

    @Test
    fun anEmptyAccountReadsAsAnEmptyListNotAnError() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()

        val response = client.list(session.accessToken)
        val page: NotificationListResponse = response.body()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(page.items.isEmpty())
        assertEquals(0, page.unreadCount)
        assertNull(page.nextBefore)
    }

    @Test
    fun anAdminSendStoresTheNotificationAndPushesItToEveryRegisteredDevice() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()
        client.registerDevice(session.accessToken, deviceToken)
        client.registerDevice(session.accessToken, "fcm-token-for-the-second-device", platform = "web")

        val response = client.send(client.adminToken(), session.user.id)
        val sent: NotificationSendResponse = response.body()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, sent.devices)
        assertEquals(2, sent.delivered)
        assertEquals(0, sent.removed)

        val page: NotificationListResponse = client.list(session.accessToken).body()
        assertEquals(1, page.items.size)
        assertEquals("Đơn hàng đã xác nhận", page.items.single().title)
        assertEquals("1234", page.items.single().data["orderId"])
        assertEquals(1, page.unreadCount)
        // The type travels in the push payload so a tap can be routed without another request.
        assertEquals(NotificationType.ORDER.wire, push.sent.single().second.data["type"])
    }

    @Test
    fun aDeviceRegisteredTwiceIsOneDeviceAndTheLatestRegistrationWins() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()

        client.registerDevice(session.accessToken, deviceToken, platform = "android")
        assertEquals(HttpStatusCode.NoContent, client.registerDevice(session.accessToken, deviceToken, platform = "web").status)

        val sent: NotificationSendResponse = client.send(client.adminToken(), session.user.id).body()
        assertEquals(1, sent.devices)
    }

    @Test
    fun signingInOnASharedDeviceMovesItsTokenToTheNewAccount() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val first = client.signUp(email = "first@example.com", phone = "0333 433 969")
        val second = client.signUp(email = "second@example.com", phone = "0333 433 970")
        client.registerDevice(first.accessToken, deviceToken)
        client.registerDevice(second.accessToken, deviceToken)

        val adminToken = client.adminToken()

        assertEquals(0, client.send(adminToken, first.user.id).body<NotificationSendResponse>().devices)
        assertEquals(1, client.send(adminToken, second.user.id).body<NotificationSendResponse>().devices)
    }

    @Test
    fun aTokenTheProviderRejectsIsDeletedRatherThanRetriedForever() = testApplication {
        push = RecordingPushSender { PushOutcome.Unregistered(it) }
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()
        client.registerDevice(session.accessToken, deviceToken)
        val adminToken = client.adminToken()

        val first: NotificationSendResponse = client.send(adminToken, session.user.id).body()
        assertEquals(1, first.devices)
        assertEquals(1, first.removed)

        assertEquals(0, client.send(adminToken, session.user.id).body<NotificationSendResponse>().devices)
    }

    @Test
    fun aProviderOutageStillStoresTheNotificationAndSaysSo() = testApplication {
        push = RecordingPushSender { PushOutcome.Failed(it, "UNAVAILABLE", retryable = true) }
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()
        client.registerDevice(session.accessToken, deviceToken)

        val response = client.send(client.adminToken(), session.user.id)

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(AuthErrorCodes.PUSH_UNAVAILABLE, response.body<ErrorResponse>().code)
        // The whole point of storing first: the user still sees it in the app.
        assertEquals(1, client.list(session.accessToken).body<NotificationListResponse>().items.size)
    }

    @Test
    fun anAccountWithNoDeviceIsNotAnOutage() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()

        val response = client.send(client.adminToken(), session.user.id)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, response.body<NotificationSendResponse>().devices)
    }

    @Test
    fun readingOneNotificationClearsItAndAnswersTheNewCount() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()
        val adminToken = client.adminToken()
        client.send(adminToken, session.user.id, title = "First")
        client.send(adminToken, session.user.id, title = "Second")
        val newest = client.list(session.accessToken).body<NotificationListResponse>().items.first()

        val response = client.post(NotificationApi.read(newest.id)) { bearerAuth(session.accessToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, response.body<UnreadCountResponse>().unreadCount)
        assertTrue(client.list(session.accessToken).body<NotificationListResponse>().items.first().read)
    }

    @Test
    fun readingItTwiceIsNotFoundBecauseThereIsNothingLeftToChange() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()
        client.send(client.adminToken(), session.user.id)
        val id = client.list(session.accessToken).body<NotificationListResponse>().items.single().id
        client.post(NotificationApi.read(id)) { bearerAuth(session.accessToken) }

        val response = client.post(NotificationApi.read(id)) { bearerAuth(session.accessToken) }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(AuthErrorCodes.NOTIFICATION_NOT_FOUND, response.body<ErrorResponse>().code)
    }

    @Test
    fun oneAccountCannotReadAnothersNotification() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val owner = client.signUp(email = "owner@example.com", phone = "0333 433 969")
        val other = client.signUp(email = "other@example.com", phone = "0333 433 970")
        client.send(client.adminToken(), owner.user.id)
        val id = client.list(owner.accessToken).body<NotificationListResponse>().items.single().id

        assertEquals(HttpStatusCode.NotFound, client.post(NotificationApi.read(id)) { bearerAuth(other.accessToken) }.status)
        assertTrue(client.list(other.accessToken).body<NotificationListResponse>().items.isEmpty())
    }

    @Test
    fun readAllClearsTheBadgeInOneCall() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()
        val adminToken = client.adminToken()
        repeat(3) { client.send(adminToken, session.user.id, title = "N$it") }

        val response = client.post(NotificationApi.READ_ALL) { bearerAuth(session.accessToken) }

        assertEquals(0, response.body<UnreadCountResponse>().unreadCount)
        assertEquals(
            0,
            client.get(NotificationApi.UNREAD_COUNT) {
                bearerAuth(session.accessToken)
            }.body<UnreadCountResponse>().unreadCount,
        )
    }

    @Test
    fun aFullPageCarriesACursorAndTheLastPageDoesNot() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()
        val adminToken = client.adminToken()
        repeat(3) { client.send(adminToken, session.user.id, title = "N$it") }

        val first: NotificationListResponse = client.list(session.accessToken, limit = 2).body()
        assertEquals(listOf("N2", "N1"), first.items.map { it.title })
        val cursor = requireNotNull(first.nextBefore)

        val second: NotificationListResponse = client.list(session.accessToken, limit = 2, before = cursor).body()
        assertEquals(listOf("N0"), second.items.map { it.title })
        assertNull(second.nextBefore)
        // The count is the account's, not the page's, because it drives the red dot.
        assertEquals(3, second.unreadCount)
    }

    @Test
    fun aCursorThatIsNotANumberIsRefusedRatherThanSilentlyIgnored() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()

        val response = client.get(NotificationApi.LIST) {
            bearerAuth(session.accessToken)
            parameter(NotificationApi.BEFORE_QUERY, "yesterday")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(AuthErrorCodes.INVALID_REQUEST, response.body<ErrorResponse>().code)
    }

    @Test
    fun anOversizedPageIsClampedInsteadOfRefused() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()

        assertEquals(HttpStatusCode.OK, client.list(session.accessToken, limit = 10_000).status)
    }

    @Test
    fun anUnusableDeviceTokenIsRefused() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()

        val tooShort = client.registerDevice(session.accessToken, "abc")
        assertEquals(HttpStatusCode.BadRequest, tooShort.status)
        assertEquals(AuthErrorCodes.INVALID_REQUEST, tooShort.body<ErrorResponse>().code)

        assertEquals(
            HttpStatusCode.BadRequest,
            client.registerDevice(session.accessToken, "token with spaces and more").status,
        )
        assertEquals(HttpStatusCode.BadRequest, client.registerDevice(session.accessToken, deviceToken, platform = "fridge").status)
    }

    @Test
    fun signingOutCanAlwaysDetachTheDeviceEvenWithTheFeatureSwitchedOff() = testApplication {
        application { module(services(flagDefaults = FeatureFlags.defaults)) }
        val client = jsonClient()
        val session = client.signUp()

        val response = client.post(NotificationApi.DEVICES_UNREGISTER) {
            bearerAuth(session.accessToken)
            contentType(ContentType.Application.Json)
            setBody(DeviceUnregisterRequest(deviceToken))
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun unregisteringStopsTheFanOutToThatDevice() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()
        client.registerDevice(session.accessToken, deviceToken)

        client.post(NotificationApi.DEVICES_UNREGISTER) {
            bearerAuth(session.accessToken)
            contentType(ContentType.Application.Json)
            setBody(DeviceUnregisterRequest(deviceToken))
        }

        assertEquals(0, client.send(client.adminToken(), session.user.id).body<NotificationSendResponse>().devices)
    }

    @Test
    fun aCustomerMayNotSendNotifications() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()

        assertEquals(HttpStatusCode.Forbidden, client.send(session.accessToken, session.user.id).status)
    }

    @Test
    fun sendValidatesTheRequestBeforeStoringAnything() = testApplication {
        application { module(services()) }
        val client = jsonClient()
        val session = client.signUp()
        val adminToken = client.adminToken()

        val blankTitle = client.send(adminToken, session.user.id, title = "   ")
        assertEquals(HttpStatusCode.BadRequest, blankTitle.status)

        assertEquals(HttpStatusCode.BadRequest, client.send(adminToken, session.user.id, title = "t".repeat(121)).status)
        assertEquals(HttpStatusCode.BadRequest, client.send(adminToken, session.user.id, body = "b".repeat(501)).status)
        // An unknown type would otherwise hit the column's CHECK constraint as a 500.
        assertEquals(HttpStatusCode.BadRequest, client.send(adminToken, session.user.id, type = "newsletter").status)

        assertTrue(client.list(session.accessToken).body<NotificationListResponse>().items.isEmpty())
    }

    @Test
    fun sendIsRefusedWhileTheFeatureIsOffEvenForAnAdministrator() = testApplication {
        application { module(services(flagDefaults = FeatureFlags.defaults)) }
        val client = jsonClient()
        val session = client.signUp()

        val response = client.send(client.adminToken(), session.user.id)

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(AuthErrorCodes.FEATURE_DISABLED, response.body<ErrorResponse>().code)
        assertEquals(0, notifications.unreadCount(session.user.id), "nothing may be stored while the feature is off")
        assertTrue(push.sent.isEmpty())
    }

    @Test
    fun theRecipientsOverrideDecidesNotTheAdministratorsOwn() = testApplication {
        application { module(services(flagDefaults = FeatureFlags.defaults)) }
        val client = jsonClient()
        val optedIn = client.signUp(email = "in@example.com", phone = "0333 433 969")
        val optedOut = client.signUp(email = "out@example.com", phone = "0333 433 970")
        // The administrator's own state stays off: the flag is resolved for whoever receives it.
        built.featureFlags.setForUser(FeatureFlag.NOTIFICATIONS, optedIn.user.id, enabled = true, note = null, actor = "test")
        val adminToken = client.adminToken()

        assertEquals(HttpStatusCode.OK, client.send(adminToken, optedIn.user.id).status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.send(adminToken, optedOut.user.id).status)
    }

    @Test
    fun sendToAnUnknownUserIsNotFound() = testApplication {
        application { module(services()) }
        val client = jsonClient()

        val response = client.send(client.adminToken(), userId = "00000000-0000-0000-0000-000000000000")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
