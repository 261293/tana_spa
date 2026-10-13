package com.tana.spa.notifications

import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.notifications.PostNotificationsPrompt

/**
 * Bridges `core:notifications` to the one thing only the app shell has: an Activity to show the
 * `POST_NOTIFICATIONS` dialog from. [MainActivity][com.tana.spa.MainActivity] attaches itself
 * while it is alive.
 *
 * Answering false with nothing attached is the safe direction: the caller treats it as "the user
 * did not allow it" and registers nothing, rather than waiting for a dialog that cannot appear.
 */
object NotificationPermissionRequests : PostNotificationsPrompt {

    @Volatile
    private var prompt: (suspend () -> Boolean)? = null

    fun attach(prompt: suspend () -> Boolean) {
        this.prompt = prompt
    }

    fun detach() {
        prompt = null
    }

    override suspend fun requestPostNotifications(): Boolean {
        val current = prompt ?: run {
            AppLog.w(TAG) { "no Activity attached, cannot show the notification permission dialog" }
            return false
        }
        return current()
    }

    private const val TAG = "AndroidPush"
}
