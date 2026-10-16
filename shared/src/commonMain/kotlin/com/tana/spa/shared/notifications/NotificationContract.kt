package com.tana.spa.shared.notifications

import com.tana.spa.shared.helpers.PlatformEnum
import kotlinx.serialization.Serializable

/** Wire contract of the notification API, shared by the Ktor server and every client target. */
object NotificationApi {
    const val BASE = "/api/v1/notifications"

    const val LIMIT_QUERY = "limit"
    const val BEFORE_QUERY = "before"
    const val ID_PARAM = "id"

    // Route templates, relative to BASE: the server mounts these and the absolute paths below
    // are built from them, so a client and the server cannot drift apart.
    const val UNREAD_COUNT_PATH = "/unread-count"
    const val READ_ALL_PATH = "/read-all"
    const val DEVICES_PATH = "/devices"

    /**
     * Unregister posts the token in a body instead of taking a `DELETE /devices/{token}`: a path
     * lands in every access log, and a push token is enough to send to that device.
     */
    const val DEVICES_UNREGISTER_PATH = "/devices/unregister"

    /** Admin-only fan-out, so delivery can be operated and smoke-tested without a staging release. */
    const val SEND_PATH = "/send"
    const val READ_PATH = "/{$ID_PARAM}/read"

    const val LIST = BASE
    const val UNREAD_COUNT = BASE + UNREAD_COUNT_PATH
    const val READ_ALL = BASE + READ_ALL_PATH
    const val DEVICES = BASE + DEVICES_PATH
    const val DEVICES_UNREGISTER = BASE + DEVICES_UNREGISTER_PATH
    const val SEND = BASE + SEND_PATH

    fun read(notificationId: String): String = "$BASE/$notificationId/read"
}

/** The `type` column's CHECK constraint (`V4__engagement.sql`) accepts exactly these. */
enum class NotificationType(val wire: String) {
    ORDER("order"),
    BOOKING("booking"),
    PROMOTION("promotion"),
    SYSTEM("system"),
    ;

    companion object {
        fun fromWire(wire: String): NotificationType? = entries.firstOrNull { it.wire == wire }
    }
}

/** The keys in a notification's `data`, written by the server and read by the app. */
object NotificationData {
    /** On a [NotificationType.BOOKING]: the visit it is about, which the app opens. */
    const val BOOKING_ID = "bookingId"

    const val BOOKING_REFERENCE = "reference"
}

/** Where a push token came from. Desktop has no push transport and never registers. */
enum class DevicePlatform(val wire: String) {
    ANDROID("android"),
    IOS("ios"),
    WEB("web"),
    DESKTOP("desktop"),
    ;

    companion object {
        fun fromWire(wire: String): DevicePlatform? = entries.firstOrNull { it.wire == wire }

        fun of(platform: PlatformEnum): DevicePlatform = when (platform) {
            PlatformEnum.ANDROID -> ANDROID
            PlatformEnum.IOS -> IOS
            PlatformEnum.WEB -> WEB
            PlatformEnum.JVM -> DESKTOP
        }
    }
}

object PushProviders {
    /** Firebase Cloud Messaging: one transport for Android, Web Push and APNs. */
    const val FCM = "fcm"
}

object PushChannels {
    /**
     * Android notification channel. The app creates it and the server names it in every message:
     * Android 8+ silently drops a notification whose channel does not exist, so the two sides
     * have to agree on this string.
     */
    const val DEFAULT = "tana_default"
}

/**
 * [type] stays a string rather than [NotificationType] so a server that learns a new type does not
 * break the parse in an app already installed; unknown values render as a plain entry.
 * [createdAtEpochMillis] is UTC.
 */
@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String? = null,
    /** Deep-link payload, e.g. `{"orderId": "..."}`. */
    val data: Map<String, String> = emptyMap(),
    val read: Boolean = false,
    val createdAtEpochMillis: Long,
)

@Serializable
data class NotificationListResponse(
    val items: List<NotificationDto>,
    /** Across the whole account, not just this page: it drives the red dot. */
    val unreadCount: Int,
    /**
     * Opaque paging cursor: echo it back as `before` for the next page, null when the list
     * ends. It is a timestamp at the precision the database keeps, which is finer than
     * [NotificationDto.createdAtEpochMillis], so pass it on rather than comparing it.
     */
    val nextBefore: Long? = null,
)

@Serializable
data class UnreadCountResponse(val unreadCount: Int)

/**
 * [token] is the provider's registration token, which belongs to the device rather than the
 * account: registering it moves it to the signed-in user, so the previous user of a shared
 * device stops receiving pushes meant for this one.
 */
@Serializable
data class DeviceRegistrationRequest(
    val token: String,
    /** [DevicePlatform.wire]. */
    val platform: String,
    val provider: String = PushProviders.FCM,
    /** BCP-47, for localising the push text later. */
    val locale: String? = null,
    val appVersion: String? = null,
)

@Serializable
data class DeviceUnregisterRequest(val token: String)

@Serializable
data class NotificationSendRequest(
    val userId: String,
    val type: String = NotificationType.SYSTEM.wire,
    val title: String,
    val body: String? = null,
    val data: Map<String, String> = emptyMap(),
)

/**
 * [removed] counts tokens the provider rejected as unregistered, which this call deleted. The row
 * is stored even when [devices] is 0, so the notification still shows up in the app.
 */
@Serializable
data class NotificationSendResponse(val notificationId: String, val devices: Int, val delivered: Int, val removed: Int)
