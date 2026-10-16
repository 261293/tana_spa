package com.tana.spa.feature.notifications.presentation

import com.tana.spa.feature.notifications.domain.NotificationItem

/**
 * [items] empty with [loading] false and [failed] false is a genuinely empty centre, which the
 * screen draws as an empty state rather than a blank page.
 */
data class NotificationsState(
    val items: List<NotificationItem> = emptyList(),
    val unreadCount: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val failed: Boolean = false,
    val nextCursor: Long? = null,
    /** True while the user could still be asked to allow notifications on this device. */
    val canEnablePush: Boolean = false,
) {
    val canLoadMore: Boolean = nextCursor != null && !loadingMore && !loading
}

sealed interface NotificationsEvent {
    data object Refresh : NotificationsEvent
    data object LoadMore : NotificationsEvent
    data class ItemClicked(val id: String) : NotificationsEvent
    data object MarkAllRead : NotificationsEvent
    data object EnablePushClicked : NotificationsEvent
}

sealed interface NotificationsEffect {
    /** The list could not be loaded or a read could not be recorded. */
    data object ShowLoadFailed : NotificationsEffect
    data object ShowPushEnabled : NotificationsEffect
    data object ShowPushRefused : NotificationsEffect

    /** A notification about a visit was tapped: the guest's own list opens on that booking. */
    data class OpenBooking(val bookingId: String) : NotificationsEffect
}
