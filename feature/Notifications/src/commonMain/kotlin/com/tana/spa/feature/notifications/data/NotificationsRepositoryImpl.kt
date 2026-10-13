package com.tana.spa.feature.notifications.data

import com.tana.spa.core.notifications.NotificationBadge
import com.tana.spa.core.notifications.data.NotificationRemoteApi
import com.tana.spa.feature.notifications.domain.NotificationItem
import com.tana.spa.feature.notifications.domain.NotificationPage
import com.tana.spa.feature.notifications.domain.NotificationsRepository
import com.tana.spa.shared.notifications.NotificationDto
import com.tana.spa.shared.notifications.NotificationType

/**
 * Every call feeds the authoritative unread count straight into [NotificationBadge], so the bell
 * and the list can never disagree about whether there is anything new.
 */
class NotificationsRepositoryImpl(private val remote: NotificationRemoteApi, private val badge: NotificationBadge) :
    NotificationsRepository {

    override suspend fun page(cursor: Long?): NotificationPage {
        val response = remote.list(before = cursor)
        badge.set(response.unreadCount)
        return NotificationPage(
            items = response.items.map { it.toItem() },
            unreadCount = response.unreadCount,
            nextCursor = response.nextBefore,
        )
    }

    override suspend fun markRead(notificationId: String): Int {
        // Null means the server had nothing unread under that id, so the count has not moved.
        val remaining = remote.markRead(notificationId) ?: remote.unreadCount()
        badge.set(remaining)
        return remaining
    }

    override suspend fun markAllRead(): Int {
        val remaining = remote.markAllRead()
        badge.set(remaining)
        return remaining
    }

    private fun NotificationDto.toItem() = NotificationItem(
        id = id,
        type = NotificationType.fromWire(type),
        title = title,
        body = body,
        data = data,
        read = read,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
