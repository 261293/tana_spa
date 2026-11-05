package com.tana.spa.core.notifications.data

import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.network.NetworkClient
import com.tana.spa.core.network.withDefaults
import com.tana.spa.shared.notifications.DeviceRegistrationRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private data class Sent(val call: String, val body: String, val authorization: String?)

class NotificationRemoteApiTest {

    private val sent = mutableListOf<Sent>()

    private fun api(reply: String, status: HttpStatusCode = HttpStatusCode.OK): NotificationRemoteApi {
        val engine = MockEngine { request ->
            sent += Sent(
                "${request.method.value} ${request.url.encodedPath}",
                request.body.toByteArray().decodeToString(),
                request.headers[HttpHeaders.Authorization],
            )
            respond(reply, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return NotificationRemoteApiImpl(NetworkClient(HttpClient(engine).withDefaults()), "http://api.test", AccessTokenSource { "a1" })
    }

    @Test
    fun list_posts_the_paging_parameters_to_notifications_list() = runTest {
        val page = api("""{"items":[],"unreadCount":2,"nextBefore":null}""").list(limit = 20, before = 1234)

        assertEquals(Sent("POST /rest/v1/rpc/notifications_list", """{"limit":20,"before":1234}""", "Bearer a1"), sent.single())
        assertEquals(2, page.unreadCount)
    }

    @Test
    fun list_without_paging_sends_an_empty_object() = runTest {
        api("""{"items":[],"unreadCount":0}""").list()

        assertEquals("{}", sent.single().body)
    }

    @Test
    fun unread_count_posts_to_notifications_unread_count() = runTest {
        assertEquals(3, api("""{"unreadCount":3}""").unreadCount())
        assertEquals("POST /rest/v1/rpc/notifications_unread_count", sent.single().call)
    }

    @Test
    fun mark_read_posts_the_id_to_notifications_mark_read() = runTest {
        assertEquals(1, api("""{"unreadCount":1}""").markRead("n1"))
        assertEquals(Sent("POST /rest/v1/rpc/notifications_mark_read", """{"id":"n1"}""", "Bearer a1"), sent.single())
    }

    @Test
    fun mark_read_of_nothing_unread_is_null() = runTest {
        assertNull(api("""{"code":"notification_not_found","message":"gone"}""", HttpStatusCode.NotFound).markRead("n1"))
    }

    @Test
    fun mark_all_read_posts_to_notifications_mark_all_read() = runTest {
        assertEquals(0, api("""{"unreadCount":0}""").markAllRead())
        assertEquals("POST /rest/v1/rpc/notifications_mark_all_read", sent.single().call)
    }

    @Test
    fun register_device_posts_the_request_and_accepts_an_empty_200() = runTest {
        api("").registerDevice(DeviceRegistrationRequest(token = "t1", platform = "android", locale = "vi-VN"))

        assertEquals(
            Sent("POST /rest/v1/rpc/register_device", """{"token":"t1","platform":"android","locale":"vi-VN"}""", "Bearer a1"),
            sent.single(),
        )
    }

    @Test
    fun unregister_device_posts_the_token_and_accepts_204() = runTest {
        api("", HttpStatusCode.NoContent).unregisterDevice("t1")

        assertEquals(Sent("POST /rest/v1/rpc/unregister_device", """{"token":"t1"}""", "Bearer a1"), sent.single())
    }

    @Test
    fun a_disabled_feature_carries_its_code() = runTest {
        val failure = assertFailsWith<NotificationApiException> {
            api("""{"code":"feature_disabled","message":"off"}""", HttpStatusCode.ServiceUnavailable).unreadCount()
        }

        assertEquals(503 to "feature_disabled", failure.status to failure.code)
    }
}
