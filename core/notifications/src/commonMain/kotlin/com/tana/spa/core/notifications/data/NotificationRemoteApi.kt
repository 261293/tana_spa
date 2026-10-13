package com.tana.spa.core.notifications.data

import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.network.NetworkClient
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.notifications.DeviceRegistrationRequest
import com.tana.spa.shared.notifications.DeviceUnregisterRequest
import com.tana.spa.shared.notifications.NotificationApi
import com.tana.spa.shared.notifications.NotificationListResponse
import com.tana.spa.shared.notifications.UnreadCountResponse
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Every call needs a session; all of them throw on any non-success status. */
interface NotificationRemoteApi {
    suspend fun list(limit: Int? = null, before: Long? = null): NotificationListResponse

    suspend fun unreadCount(): Int

    /**
     * @return the account's remaining unread count, or null when the server says there was
     *   nothing unread with that id (already read, or gone).
     */
    suspend fun markRead(notificationId: String): Int?

    suspend fun markAllRead(): Int

    suspend fun registerDevice(request: DeviceRegistrationRequest)

    suspend fun unregisterDevice(token: String)
}

/**
 * [code] is the server's machine-readable error code when it sent one, so a caller can tell
 * `feature_disabled` (stop asking) from a 500 (retry later).
 */
class NotificationApiException(val status: Int, val code: String?) :
    Exception("Notification request failed with $status" + (code?.let { " ($it)" } ?: ""))

class NotificationRemoteApiImpl(
    private val networkClient: NetworkClient,
    private val baseUrl: String,
    private val tokens: AccessTokenSource,
) : NotificationRemoteApi {

    override suspend fun list(limit: Int?, before: Long?): NotificationListResponse {
        val response = networkClient.httpClient.get(baseUrl + NotificationApi.LIST) {
            authorize()
            limit?.let { parameter(NotificationApi.LIMIT_QUERY, it) }
            before?.let { parameter(NotificationApi.BEFORE_QUERY, it) }
        }
        return response.requireSuccess().body()
    }

    override suspend fun unreadCount(): Int {
        val response = networkClient.httpClient.get(baseUrl + NotificationApi.UNREAD_COUNT) { authorize() }
        return response.requireSuccess().body<UnreadCountResponse>().unreadCount
    }

    override suspend fun markRead(notificationId: String): Int? {
        val response = networkClient.httpClient.post(baseUrl + NotificationApi.read(notificationId)) { authorize() }
        // Already read, or gone: the client's view is simply out of date, which is not an error.
        if (response.status == HttpStatusCode.NotFound) return null
        return response.requireSuccess().body<UnreadCountResponse>().unreadCount
    }

    override suspend fun markAllRead(): Int {
        val response = networkClient.httpClient.post(baseUrl + NotificationApi.READ_ALL) { authorize() }
        return response.requireSuccess().body<UnreadCountResponse>().unreadCount
    }

    override suspend fun registerDevice(request: DeviceRegistrationRequest) {
        networkClient.httpClient.post(baseUrl + NotificationApi.DEVICES) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess()
    }

    override suspend fun unregisterDevice(token: String) {
        networkClient.httpClient.post(baseUrl + NotificationApi.DEVICES_UNREGISTER) {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(DeviceUnregisterRequest(token))
        }.requireSuccess()
    }

    private fun HttpRequestBuilder.authorize() {
        tokens.accessToken()?.let { bearerAuth(it) }
    }

    private suspend fun HttpResponse.requireSuccess(): HttpResponse {
        if (status.isSuccess()) return this
        val code = runCatching { body<ErrorResponse>().code }.getOrNull()
        throw NotificationApiException(status.value, code)
    }
}
