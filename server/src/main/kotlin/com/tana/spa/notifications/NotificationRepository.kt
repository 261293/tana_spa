package com.tana.spa.notifications

import java.util.UUID

data class NotificationRow(
    val id: String,
    val type: String,
    val title: String,
    val body: String?,
    val data: Map<String, String>,
    val read: Boolean,
    /**
     * Microseconds, the precision `timestamptz` actually stores. The paging cursor is built
     * from it, and rounding to milliseconds first would let two notifications created inside
     * the same millisecond share one cursor value, which silently drops the older of the two.
     */
    val createdAtEpochMicros: Long,
)

data class NewNotification(val userId: String, val type: String, val title: String, val body: String?, val data: Map<String, String>)

/** One registered install. [token] is the provider's, so it is the identity of the device here. */
data class DeviceTokenRow(val token: String, val platform: String, val provider: String, val locale: String?)

data class DeviceRegistration(
    val userId: String,
    val token: String,
    val platform: String,
    val provider: String,
    val locale: String?,
    val appVersion: String?,
)

/** Blocking; [NotificationService] moves the calls off the event loop. */
interface NotificationRepository {

    /** Newest first. [beforeEpochMicros] excludes anything at or after it, for keyset paging. */
    fun list(userId: String, limit: Int, beforeEpochMicros: Long?): List<NotificationRow>

    fun unreadCount(userId: String): Int

    /** @return false when no unread notification with that id belongs to [userId]. */
    fun markRead(userId: String, notificationId: String): Boolean

    fun markAllRead(userId: String): Int

    fun insert(notification: NewNotification): String

    fun markPushed(notificationId: String)

    fun devicesOf(userId: String): List<DeviceTokenRow>

    /** Upsert on the token, moving it to [DeviceRegistration.userId] if another account had it. */
    fun register(registration: DeviceRegistration)

    fun unregister(token: String): Boolean

    /** @return how many rows went away. Used to drop tokens the provider rejected. */
    fun deleteTokens(tokens: Collection<String>): Int
}

/** Used when `DATABASE_URL` is unset, and by the route tests. Everything is lost on restart. */
class InMemoryNotificationRepository : NotificationRepository {

    private class Stored(val id: String, val userId: String, val notification: NotificationRow, var read: Boolean)

    private val notifications = mutableListOf<Stored>()
    private val devices = LinkedHashMap<String, DeviceRegistration>()

    @Synchronized
    override fun list(userId: String, limit: Int, beforeEpochMicros: Long?): List<NotificationRow> = notifications
        .asReversed()
        .filter {
            it.userId == userId && (beforeEpochMicros == null || it.notification.createdAtEpochMicros < beforeEpochMicros)
        }
        .take(limit)
        .map { it.notification.copy(read = it.read) }

    @Synchronized
    override fun unreadCount(userId: String): Int = notifications.count { it.userId == userId && !it.read }

    @Synchronized
    override fun markRead(userId: String, notificationId: String): Boolean {
        val stored = notifications.firstOrNull { it.id == notificationId && it.userId == userId } ?: return false
        if (stored.read) return false
        stored.read = true
        return true
    }

    @Synchronized
    override fun markAllRead(userId: String): Int = notifications
        .filter { it.userId == userId && !it.read }
        .onEach { it.read = true }
        .size

    @Synchronized
    override fun insert(notification: NewNotification): String {
        val id = UUID.randomUUID().toString()
        notifications += Stored(
            id = id,
            userId = notification.userId,
            notification = NotificationRow(
                id = id,
                type = notification.type,
                title = notification.title,
                body = notification.body,
                data = notification.data,
                read = false,
                // Monotonic within a run: the clock can repeat inside one tick, and the paging
                // cursor is a timestamp.
                createdAtEpochMicros = nextCreatedAt(),
            ),
            read = false,
        )
        return id
    }

    @Synchronized
    override fun markPushed(notificationId: String) = Unit

    @Synchronized
    override fun devicesOf(userId: String): List<DeviceTokenRow> = devices.values
        .filter { it.userId == userId }
        .map { DeviceTokenRow(it.token, it.platform, it.provider, it.locale) }

    @Synchronized
    override fun register(registration: DeviceRegistration) {
        devices[registration.token] = registration
    }

    @Synchronized
    override fun unregister(token: String): Boolean = devices.remove(token) != null

    @Synchronized
    override fun deleteTokens(tokens: Collection<String>): Int = tokens.count { devices.remove(it) != null }

    private var lastCreatedAt = 0L

    private fun nextCreatedAt(): Long {
        lastCreatedAt = maxOf(System.currentTimeMillis() * 1_000, lastCreatedAt + 1)
        return lastCreatedAt
    }
}
