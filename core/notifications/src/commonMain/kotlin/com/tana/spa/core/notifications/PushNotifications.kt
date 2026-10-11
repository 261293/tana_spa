package com.tana.spa.core.notifications

import com.tana.spa.shared.notifications.DevicePlatform

/**
 * [NOT_DETERMINED] is the only state worth prompting from. [DENIED] means the user said no and
 * only the system settings can change that, so the app must not nag; [UNSUPPORTED] is a platform
 * with no push transport (the desktop build, or a browser without the Notification API).
 */
enum class PushPermission { GRANTED, DENIED, NOT_DETERMINED, UNSUPPORTED }

interface PushPermissions {
    suspend fun status(): PushPermission

    /**
     * Shows the system prompt when the state is [PushPermission.NOT_DETERMINED] and returns the
     * state afterwards. Call it from a moment the user understands — the notification centre, or
     * after an order — never on first launch: the prompt is only ever shown once.
     */
    suspend fun request(): PushPermission

    companion object {
        val unsupported: PushPermissions = object : PushPermissions {
            override suspend fun status() = PushPermission.UNSUPPORTED

            override suspend fun request() = PushPermission.UNSUPPORTED
        }
    }
}

/**
 * This install's push registration token, supplied by whatever vendor SDK the platform uses. The
 * token is a credential of sorts: it is never logged in full and never stored anywhere but the
 * server.
 */
interface PushTokens {
    val platform: DevicePlatform

    /** BCP-47 language of the device, so the server can localise a push later. */
    val locale: String?

    val appVersion: String?

    /** Null when no token can be obtained: push is not configured, or the platform has none. */
    suspend fun current(): String?

    /** Drops the token at the provider, so an uninstalled or signed-out device stops receiving. */
    suspend fun delete()
}

/** The desktop build, and any target whose push transport is not configured. */
class NoPushTokens(override val platform: DevicePlatform) : PushTokens {
    override val locale: String? = null
    override val appVersion: String? = null

    override suspend fun current(): String? = null

    override suspend fun delete() = Unit
}
