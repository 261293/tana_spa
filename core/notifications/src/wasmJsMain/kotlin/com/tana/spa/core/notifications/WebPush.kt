package com.tana.spa.core.notifications

import com.tana.spa.core.logging.AppLog
import com.tana.spa.shared.notifications.DevicePlatform
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Web Push through Firebase's compat SDK, which the page loads itself; see
 * `docs/notifications.md` for the two script tags, the VAPID key and the service worker.
 *
 * Every browser call goes through a `js(...)` body rather than an `external` declaration on
 * purpose: a missing external would make the whole wasm module fail to instantiate — a white
 * screen — whereas these snippets test for the API and answer "no push" when it is absent.
 */
private fun notificationApiPresent(): Boolean = js("typeof Notification !== 'undefined'")

private fun notificationPermission(): String = js("Notification.permission")

private fun requestNotificationPermission(): Promise<JsString> = js("Notification.requestPermission()")

/**
 * Resolves to null — never rejects — when the page has not loaded the Firebase SDK, when no VAPID
 * key is configured, or when the browser refuses the service worker. "No token" is an ordinary
 * state for a browser, not an error worth surfacing.
 */
private fun requestFcmToken(): Promise<JsString?> = js(
    """(function () {
         var key = window.TANA_PUSH_VAPID_KEY;
         if (typeof firebase === 'undefined' || !firebase.messaging || !key) return Promise.resolve(null);
         if (!('serviceWorker' in navigator)) return Promise.resolve(null);
         return navigator.serviceWorker.register('firebase-messaging-sw.js')
           .then(function (registration) {
             return firebase.messaging().getToken({ vapidKey: key, serviceWorkerRegistration: registration });
           })
           .then(function (token) { return token || null; })
           .catch(function () { return null; });
       })()""",
)

private fun deleteFcmToken(): Promise<JsAny?> = js(
    """(function () {
         if (typeof firebase === 'undefined' || !firebase.messaging) return Promise.resolve(null);
         return firebase.messaging().deleteToken().catch(function () { return null; });
       })()""",
)

private fun browserLanguage(): String? = js("navigator.language || null")

class WebPushPermissions : PushPermissions {

    override suspend fun status(): PushPermission = when {
        !notificationApiPresent() -> PushPermission.UNSUPPORTED
        else -> notificationPermission().toPermission()
    }

    override suspend fun request(): PushPermission {
        if (!notificationApiPresent()) return PushPermission.UNSUPPORTED
        val current = notificationPermission().toPermission()
        // Asking again after a refusal is a no-op in every browser, and some of them log a warning.
        if (current != PushPermission.NOT_DETERMINED) return current
        return try {
            requestNotificationPermission().await().toString().toPermission()
        } catch (e: Throwable) {
            AppLog.w(TAG) { "the browser refused the permission prompt (${e::class.simpleName})" }
            PushPermission.DENIED
        }
    }

    private fun String.toPermission(): PushPermission = when (this) {
        "granted" -> PushPermission.GRANTED
        "denied" -> PushPermission.DENIED
        else -> PushPermission.NOT_DETERMINED
    }

    private companion object {
        const val TAG = "WebPush"
    }
}

class WebPushTokens : PushTokens {

    override val platform = DevicePlatform.WEB

    override val locale: String? get() = browserLanguage()

    /** The browser build has no installable version to report. */
    override val appVersion: String? = null

    override suspend fun current(): String? = requestFcmToken().await()?.toString()?.takeIf { it.isNotBlank() }

    override suspend fun delete() {
        deleteFcmToken().await()
    }
}
