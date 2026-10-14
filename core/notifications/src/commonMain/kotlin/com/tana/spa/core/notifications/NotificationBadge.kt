package com.tana.spa.core.notifications

import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.notifications.data.NotificationRemoteApi
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException

/**
 * The red dot on the bell. Lives in `core` because two unrelated places read it: the app bar, in
 * every screen's chrome, and the notification centre, which writes the new count back after the
 * user reads something — neither should have to know about the other.
 */
class NotificationBadge(
    private val remote: NotificationRemoteApi,
    private val flags: FeatureFlags,
    private val sessions: AccessTokenSource,
) {
    private val state = MutableStateFlow(0)

    val unreadCount: StateFlow<Int> = state.asStateFlow()

    /** Silent on failure: a count that cannot be fetched is shown as the last known one. */
    suspend fun refresh() {
        if (!flags.isEnabled(FeatureFlag.NOTIFICATIONS) || sessions.accessToken() == null) {
            state.value = 0
            return
        }
        try {
            state.value = remote.unreadCount()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Ktor's wasm engine reports a failed fetch as kotlin.Error, which is not an Exception.
            AppLog.w(TAG, e) { "unread count unavailable (${e::class.simpleName})" }
        }
    }

    /** Set from an answer that already carries the authoritative count, so the bell never lags. */
    fun set(count: Int) {
        state.value = count.coerceAtLeast(0)
    }

    /** On sign-out: the next account's count is not this one's. */
    fun clear() {
        state.value = 0
    }

    private companion object {
        const val TAG = "NotificationBadge"
    }
}
