package com.tana.spa.core.notifications

import com.tana.spa.core.cache.KeyValueCache
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.notifications.data.NotificationApiException
import com.tana.spa.core.notifications.data.NotificationRemoteApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlags
import com.tana.spa.shared.notifications.DeviceRegistrationRequest
import kotlin.coroutines.cancellation.CancellationException

sealed interface PushRegistration {
    data object Registered : PushRegistration

    /** Nothing to do, and nothing wrong. [why] is for the log, not for the user. */
    data class Skipped(val why: Reason) : PushRegistration

    /** The call failed; the next attempt will try again because nothing was cached. */
    data class Failed(val reason: String) : PushRegistration

    enum class Reason {
        FEATURE_OFF,
        NOT_SIGNED_IN,
        PERMISSION_NOT_GRANTED,
        NO_TOKEN,

        /** This exact token is already registered for this session. */
        UNCHANGED,
    }
}

/**
 * Owns the lifecycle of one device's push registration: ask the platform for a token, hand it to
 * the server, and take it back on sign-out.
 *
 * Registration is idempotent on purpose — it runs on every launch, because a token can be rotated
 * by the provider at any time — so the last token sent is cached and an unchanged one costs
 * nothing. The cache holds only the token, never the session.
 */
class PushRegistrar(
    private val remote: NotificationRemoteApi,
    private val cache: KeyValueCache,
    private val permissions: PushPermissions,
    private val tokens: PushTokens,
    private val flags: FeatureFlags,
    private val sessions: AccessTokenSource,
) {
    /**
     * Safe to call on every launch and after every sign-in. Never prompts: it registers only a
     * permission the user has already granted, so a launch cannot burn the one-shot system prompt.
     *
     * @param force re-registers an unchanged token, which is what moves the device to the account
     *   that just signed in.
     */
    suspend fun registerIfPossible(force: Boolean = false): PushRegistration {
        if (!flags.isEnabled(FeatureFlag.NOTIFICATIONS)) return skip(PushRegistration.Reason.FEATURE_OFF)
        // The endpoint is authenticated, so an anonymous call could only ever be a 401.
        if (sessions.accessToken() == null) return skip(PushRegistration.Reason.NOT_SIGNED_IN)
        if (permissions.status() != PushPermission.GRANTED) return skip(PushRegistration.Reason.PERMISSION_NOT_GRANTED)
        return register(force)
    }

    /**
     * Prompts when the user has not been asked yet, then registers. Call this from a screen where
     * the user has just shown interest in being notified.
     */
    suspend fun requestPermissionAndRegister(): PushRegistration {
        if (!flags.isEnabled(FeatureFlag.NOTIFICATIONS)) return skip(PushRegistration.Reason.FEATURE_OFF)
        if (sessions.accessToken() == null) return skip(PushRegistration.Reason.NOT_SIGNED_IN)
        val granted = permissions.request()
        AppLog.i(TAG) { "permission after prompt: $granted" }
        if (granted != PushPermission.GRANTED) return skip(PushRegistration.Reason.PERMISSION_NOT_GRANTED)
        return register(force = true)
    }

    /**
     * Stops pushes to this device. Called on sign-out, where it matters: the token outlives the
     * session, so without this the next person to use the device would receive the last user's
     * notifications.
     */
    suspend fun unregister() {
        val token = readCachedToken() ?: runCatching { tokens.current() }.getOrNull() ?: return
        try {
            remote.unregisterDevice(token)
            AppLog.i(TAG) { "device unregistered" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Ktor's wasm engine reports a failed fetch as kotlin.Error, which is not an Exception.
            // The server may still push here until the token is rejected or re-registered. Not
            // worth blocking sign-out for; the token is dropped locally either way.
            AppLog.w(TAG, e) { "could not unregister the device (${e::class.simpleName})" }
        } finally {
            writeCachedToken(null)
        }
    }

    private suspend fun register(force: Boolean): PushRegistration {
        val token = try {
            tokens.current()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // No Firebase configuration, no Play services, a browser that blocked the service
            // worker: all of them mean "no push on this device", not a broken app.
            AppLog.w(TAG, e) { "no push token available (${e::class.simpleName})" }
            null
        }
        if (token.isNullOrBlank()) return skip(PushRegistration.Reason.NO_TOKEN)
        if (!force && token == readCachedToken()) return skip(PushRegistration.Reason.UNCHANGED)

        return try {
            remote.registerDevice(
                DeviceRegistrationRequest(
                    token = token,
                    platform = tokens.platform.wire,
                    locale = tokens.locale,
                    appVersion = tokens.appVersion,
                ),
            )
            writeCachedToken(token)
            AppLog.i(TAG) { "device registered for ${tokens.platform.wire}" }
            PushRegistration.Registered
        } catch (e: CancellationException) {
            throw e
        } catch (e: NotificationApiException) {
            // Switched off between the flag read and this call: the client's snapshot was stale.
            if (e.code == AuthErrorCodes.FEATURE_DISABLED) {
                skip(PushRegistration.Reason.FEATURE_OFF)
            } else {
                failed("HTTP ${e.status}${e.code?.let { " ($it)" } ?: ""}")
            }
        } catch (e: Throwable) {
            failed(e::class.simpleName ?: "unknown error")
        }
    }

    private fun skip(reason: PushRegistration.Reason): PushRegistration {
        AppLog.d(TAG) { "registration skipped: $reason" }
        return PushRegistration.Skipped(reason)
    }

    private fun failed(reason: String): PushRegistration {
        AppLog.w(TAG) { "registration failed: $reason" }
        return PushRegistration.Failed(reason)
    }

    private fun readCachedToken(): String? = try {
        cache.get(CACHE_KEY)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        AppLog.w(TAG, e) { "could not read the cached token (${e::class.simpleName})" }
        null
    }

    private fun writeCachedToken(token: String?) {
        try {
            cache.put(CACHE_KEY, token.orEmpty())
        } catch (e: Exception) {
            // Losing the cache only costs one extra registration call on the next launch.
            AppLog.w(TAG, e) { "could not cache the token (${e::class.simpleName})" }
        }
    }

    private companion object {
        const val TAG = "PushRegistrar"
        const val CACHE_KEY = "push.device_token.v1"
    }
}
