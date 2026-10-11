package com.tana.spa.feature.notifications.domain

import com.tana.spa.shared.notifications.NotificationType

/** One entry in the centre. [type] is the parsed form; an unknown wire value becomes null. */
data class NotificationItem(
    val id: String,
    val type: NotificationType?,
    val title: String,
    val body: String?,
    val data: Map<String, String>,
    val read: Boolean,
    val createdAtEpochMillis: Long,
)

/** [nextCursor] is null at the end of the list. */
data class NotificationPage(val items: List<NotificationItem>, val unreadCount: Int, val nextCursor: Long?)

interface NotificationsRepository {
    /** A null [cursor] asks for the newest page. Throws on any failure. */
    suspend fun page(cursor: Long? = null): NotificationPage

    /** @return the account's remaining unread count. */
    suspend fun markRead(notificationId: String): Int

    suspend fun markAllRead(): Int
}
