package com.tana.spa.notifications

import com.tana.spa.admin.requireAdmin
import com.tana.spa.admin.respondApiError
import com.tana.spa.auth.AuthService
import com.tana.spa.auth.JWT_AUTH
import com.tana.spa.auth.refuseIfDisabled
import com.tana.spa.featureflags.FeatureFlagService
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.notifications.DeviceRegistrationRequest
import com.tana.spa.shared.notifications.DeviceUnregisterRequest
import com.tana.spa.shared.notifications.NotificationApi
import com.tana.spa.shared.notifications.NotificationSendRequest
import com.tana.spa.shared.notifications.UnreadCountResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.tana.spa.notifications")

/** Install after `authModule`: it owns ContentNegotiation, CORS, StatusPages and the JWT realm. */
fun Application.notificationModule(service: NotificationService, flags: FeatureFlagService, auth: AuthService) {
    routing {
        authenticate(JWT_AUTH) {
            route(NotificationApi.BASE) {
                get {
                    val userId = call.userId()
                    if (call.refuseIfDisabled(flags, FeatureFlag.NOTIFICATIONS, userId)) return@get
                    val before = call.request.queryParameters[NotificationApi.BEFORE_QUERY]
                    val cursor = before?.toLongOrNull()
                    // Silently dropping an unreadable cursor would hand the client the first page
                    // forever, which reads as an infinite list rather than an error.
                    if (before != null && cursor == null) {
                        call.respondApiError(
                            HttpStatusCode.BadRequest,
                            AuthErrorCodes.INVALID_REQUEST,
                            "${NotificationApi.BEFORE_QUERY} must be epoch milliseconds",
                        )
                        return@get
                    }
                    val limit = call.request.queryParameters[NotificationApi.LIMIT_QUERY]?.toIntOrNull()
                    call.respond(service.list(userId, limit, cursor))
                }

                get(NotificationApi.UNREAD_COUNT_PATH) {
                    val userId = call.userId()
                    if (call.refuseIfDisabled(flags, FeatureFlag.NOTIFICATIONS, userId)) return@get
                    call.respond(UnreadCountResponse(service.unreadCount(userId)))
                }

                post(NotificationApi.READ_PATH) {
                    val userId = call.userId()
                    if (call.refuseIfDisabled(flags, FeatureFlag.NOTIFICATIONS, userId)) return@post
                    val id = call.parameters[NotificationApi.ID_PARAM].orEmpty()
                    if (service.markRead(userId, id)) {
                        call.respond(UnreadCountResponse(service.unreadCount(userId)))
                    } else {
                        // Also the answer for a notification that was already read: either way
                        // there is nothing left for this call to change.
                        call.respondApiError(
                            HttpStatusCode.NotFound,
                            AuthErrorCodes.NOTIFICATION_NOT_FOUND,
                            "No unread notification with that id",
                        )
                    }
                }

                post(NotificationApi.READ_ALL_PATH) {
                    val userId = call.userId()
                    if (call.refuseIfDisabled(flags, FeatureFlag.NOTIFICATIONS, userId)) return@post
                    val marked = service.markAllRead(userId)
                    log.info("read-all user={} marked={}", userId, marked)
                    call.respond(UnreadCountResponse(service.unreadCount(userId)))
                }

                post(NotificationApi.DEVICES_PATH) {
                    val userId = call.userId()
                    if (call.refuseIfDisabled(flags, FeatureFlag.NOTIFICATIONS, userId)) return@post
                    when (val result = service.registerDevice(userId, call.receive<DeviceRegistrationRequest>())) {
                        RegisterDeviceResult.Registered -> call.respond(HttpStatusCode.NoContent)

                        is RegisterDeviceResult.Invalid -> call.respondApiError(
                            HttpStatusCode.BadRequest,
                            AuthErrorCodes.INVALID_REQUEST,
                            "${result.field} is invalid (${result.reason})",
                        )
                    }
                }

                post(NotificationApi.DEVICES_UNREGISTER_PATH) {
                    // Not flag-gated: switching the feature off must still let a client that is
                    // signing out stop pushes to that device.
                    service.unregisterDevice(call.receive<DeviceUnregisterRequest>().token)
                    call.respond(HttpStatusCode.NoContent)
                }

                post(NotificationApi.SEND_PATH) {
                    call.requireAdmin(auth) ?: return@post
                    // No refuseIfDisabled here: the service resolves the flag for the recipient,
                    // not for the administrator who pressed send.
                    when (val result = service.send(call.receive<NotificationSendRequest>())) {
                        is SendNotificationResult.Sent -> call.respond(result.response)

                        is SendNotificationResult.Invalid -> call.respondApiError(
                            HttpStatusCode.BadRequest,
                            AuthErrorCodes.INVALID_REQUEST,
                            "${result.field} is invalid (${result.reason})",
                        )

                        SendNotificationResult.UserNotFound -> call.respondApiError(
                            HttpStatusCode.NotFound,
                            AuthErrorCodes.INVALID_REQUEST,
                            "No such user",
                        )

                        SendNotificationResult.FeatureDisabled -> call.respondApiError(
                            HttpStatusCode.ServiceUnavailable,
                            AuthErrorCodes.FEATURE_DISABLED,
                            "Notifications are switched off for this user",
                        )

                        is SendNotificationResult.PushUnavailable -> call.respondApiError(
                            HttpStatusCode.ServiceUnavailable,
                            AuthErrorCodes.PUSH_UNAVAILABLE,
                            "Stored as ${result.notificationId}, but no device could be reached",
                        )
                    }
                }
            }
        }
    }
}

/** Safe inside `authenticate(JWT_AUTH)`: the realm rejects a call without a valid subject. */
private fun ApplicationCall.userId(): String = principal<JWTPrincipal>()!!.payload.subject
