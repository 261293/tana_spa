package com.tana.spa.notifications

import com.tana.spa.auth.UserRepository
import com.tana.spa.featureflags.FeatureFlagService
import com.tana.spa.notifications.push.PushMessage
import com.tana.spa.notifications.push.PushOutcome
import com.tana.spa.notifications.push.PushSender
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.notifications.DevicePlatform
import com.tana.spa.shared.notifications.DeviceRegistrationRequest
import com.tana.spa.shared.notifications.NotificationDto
import com.tana.spa.shared.notifications.NotificationListResponse
import com.tana.spa.shared.notifications.NotificationSendRequest
import com.tana.spa.shared.notifications.NotificationSendResponse
import com.tana.spa.shared.notifications.NotificationType
import com.tana.spa.shared.notifications.NotificationValidation
import com.tana.spa.shared.notifications.PushProviders
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

sealed interface RegisterDeviceResult {
    data object Registered : RegisterDeviceResult
    data class Invalid(val field: String, val reason: String) : RegisterDeviceResult
}

sealed interface SendNotificationResult {
    data class Sent(val response: NotificationSendResponse) : SendNotificationResult
    data class Invalid(val field: String, val reason: String) : SendNotificationResult
    data object UserNotFound : SendNotificationResult

    /** Notifications are switched off for the recipient, so nothing was stored or sent. */
    data object FeatureDisabled : SendNotificationResult

    /**
     * The notification is stored and will appear in the app; only the device wake-up failed.
     * [notificationId] is returned so an operator can tell the two apart.
     */
    data class PushUnavailable(val notificationId: String, val reason: String) : SendNotificationResult
}

class NotificationService(
    private val repository: NotificationRepository,
    private val users: UserRepository,
    private val push: PushSender,
    private val flags: FeatureFlagService,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun list(userId: String, limit: Int?, cursor: Long?): NotificationListResponse = withContext(io) {
        val pageSize = NotificationValidation.pageSize(limit)
        val rows = repository.list(userId, pageSize, cursor)
        NotificationListResponse(
            items = rows.map { it.toDto() },
            unreadCount = repository.unreadCount(userId),
            // Only when the page is full: a short page is the end of the list, and a cursor there
            // would make the client fetch an empty page to find that out.
            nextBefore = rows.lastOrNull()?.createdAtEpochMicros?.takeIf { rows.size == pageSize },
        )
    }

    suspend fun unreadCount(userId: String): Int = withContext(io) { repository.unreadCount(userId) }

    /** @return false when nothing unread with that id belongs to [userId]. */
    suspend fun markRead(userId: String, notificationId: String): Boolean = withContext(io) {
        repository.markRead(userId, notificationId)
    }

    suspend fun markAllRead(userId: String): Int = withContext(io) { repository.markAllRead(userId) }

    suspend fun registerDevice(userId: String, request: DeviceRegistrationRequest): RegisterDeviceResult = withContext(io) {
        val token = request.token.trim()
        NotificationValidation.validateDeviceToken(token)?.let {
            return@withContext RegisterDeviceResult.Invalid("token", it.name)
        }
        val platform = DevicePlatform.fromWire(request.platform)
            ?: return@withContext RegisterDeviceResult.Invalid("platform", "unknown")
        if (request.provider != PushProviders.FCM) {
            return@withContext RegisterDeviceResult.Invalid("provider", "unsupported")
        }
        repository.register(
            DeviceRegistration(
                userId = userId,
                token = token,
                platform = platform.wire,
                provider = request.provider,
                locale = request.locale?.take(MAX_LOCALE_LENGTH),
                appVersion = request.appVersion?.take(MAX_APP_VERSION_LENGTH),
            ),
        )
        log.info("device registered user={} platform={} provider={}", userId, platform.wire, request.provider)
        RegisterDeviceResult.Registered
    }

    /** Always reports success: an unknown token is already in the state the caller asked for. */
    suspend fun unregisterDevice(token: String) {
        withContext(io) {
            if (repository.unregister(token.trim())) log.info("device unregistered")
        }
    }

    /**
     * Stores the notification, then pushes it to every device on the account. The row is written
     * first and on purpose: the notification centre is the durable channel, and a push is a
     * best-effort wake-up on top of it.
     *
     * Refuses outright while the notifications flag is off for the recipient, so switching the
     * feature off also stops the history filling up behind the user's back.
     */
    suspend fun send(request: NotificationSendRequest): SendNotificationResult = withContext(io) {
        validate(request)?.let { return@withContext it }
        if (users.findById(request.userId) == null) return@withContext SendNotificationResult.UserNotFound
        // Checked here rather than only at the route, so every future caller (an order service,
        // a scheduled job) is gated too. Resolved for the RECIPIENT: a per-user rollout means
        // "this person receives notifications", whoever triggered the send.
        if (!flags.isEnabled(FeatureFlag.NOTIFICATIONS, request.userId)) {
            log.info("notification for user {} refused: feature {} is switched off", request.userId, FeatureFlag.NOTIFICATIONS.key)
            return@withContext SendNotificationResult.FeatureDisabled
        }

        val id = repository.insert(
            NewNotification(
                userId = request.userId,
                type = request.type,
                title = request.title.trim(),
                body = request.body?.trim()?.takeIf { it.isNotEmpty() },
                data = request.data,
            ),
        )
        val devices = repository.devicesOf(request.userId)
        val outcomes = push.send(devices.map { it.token }, request.toPushMessage())
        val delivered = outcomes.count { it is PushOutcome.Delivered }
        val removed = repository.deleteTokens(outcomes.filterIsInstance<PushOutcome.Unregistered>().map { it.token })
        if (delivered > 0) repository.markPushed(id)

        log.info(
            "notification {} user={} type={} devices={} delivered={} removed={} via {}",
            id,
            request.userId,
            request.type,
            devices.size,
            delivered,
            removed,
            push.name,
        )

        val blocked = outcomes.filterIsInstance<PushOutcome.Failed>()
        // Every device failed and none of the failures was the device's fault: report the outage
        // rather than a clean success, so an operator sees a misconfigured sender immediately.
        if (delivered == 0 && blocked.isNotEmpty() && blocked.size == outcomes.size) {
            return@withContext SendNotificationResult.PushUnavailable(id, blocked.first().reason)
        }
        SendNotificationResult.Sent(NotificationSendResponse(id, devices.size, delivered, removed))
    }

    private fun validate(request: NotificationSendRequest): SendNotificationResult.Invalid? {
        NotificationValidation.validateTitle(request.title)?.let {
            return SendNotificationResult.Invalid("title", it.name)
        }
        NotificationValidation.validateBody(request.body)?.let {
            return SendNotificationResult.Invalid("body", it.name)
        }
        NotificationValidation.validateData(request.data)?.let {
            return SendNotificationResult.Invalid("data", it.name)
        }
        // The column's CHECK constraint would reject an unknown type as a 500; refuse it here.
        if (NotificationType.fromWire(request.type) == null) {
            return SendNotificationResult.Invalid("type", "unknown")
        }
        return null
    }

    private fun NotificationSendRequest.toPushMessage() = PushMessage(
        title = title.trim(),
        body = body?.trim()?.takeIf { it.isNotEmpty() },
        // The type travels in the payload so the app can route a tap without another round trip.
        data = data + mapOf(DATA_TYPE_KEY to type),
    )

    private fun NotificationRow.toDto() = NotificationDto(
        id = id,
        type = type,
        title = title,
        body = body,
        data = data,
        read = read,
        createdAtEpochMillis = createdAtEpochMicros / 1_000,
    )

    private companion object {
        /** `locale` and `app_version` column limits in `V8__push_notifications.sql`. */
        const val MAX_LOCALE_LENGTH = 35
        const val MAX_APP_VERSION_LENGTH = 40
        const val DATA_TYPE_KEY = "type"

        val log = LoggerFactory.getLogger(NotificationService::class.java)!!
    }
}
