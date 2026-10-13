package com.tana.spa.core.notifications

import com.tana.spa.core.logging.AppLog
import com.tana.spa.shared.notifications.DevicePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.UIKit.UIApplication
import platform.UIKit.registerForRemoteNotifications
import platform.UIKit.unregisterForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * Receives the FCM registration token from the iOS app.
 *
 * iOS is the one target where Kotlin cannot obtain the token itself: it arrives in the
 * `AppDelegate` (`didRegisterForRemoteNotificationsWithDeviceToken`, then Firebase's
 * `messaging(_:didReceiveRegistrationToken:)`), so `iosApp` calls
 * `IosPushTokenBridge.shared.onToken(...)`. See `docs/notifications.md` for the Swift side.
 */
object IosPushTokenBridge {

    private val token = MutableStateFlow<String?>(null)

    /** Called from Swift on the main thread; a null or blank value clears the stored token. */
    fun onToken(value: String?) {
        token.value = value?.takeIf { it.isNotBlank() }
        AppLog.i(TAG) { if (value == null) "token cleared by the app" else "token received from the app" }
    }

    /**
     * The token is asynchronous on iOS — APNs has to answer first — so the first launch waits a
     * short while rather than reporting "no push" for a device that is about to have a token.
     */
    internal suspend fun await(timeoutMillis: Long): String? =
        token.value ?: withTimeoutOrNull(timeoutMillis) { token.filterNotNull().first() }

    private const val TAG = "IosPush"
}

class IosPushPermissions : PushPermissions {

    override suspend fun status(): PushPermission = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            continuation.resume(
                when (status) {
                    null -> PushPermission.UNSUPPORTED

                    UNAuthorizationStatusAuthorized,
                    UNAuthorizationStatusProvisional,
                    UNAuthorizationStatusEphemeral,
                    -> PushPermission.GRANTED

                    UNAuthorizationStatusDenied -> PushPermission.DENIED

                    else -> PushPermission.NOT_DETERMINED
                },
            )
        }
    }

    override suspend fun request(): PushPermission {
        val current = status()
        if (current != PushPermission.NOT_DETERMINED) return current
        val granted = suspendCancellableCoroutine { continuation ->
            val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(options) { granted, error ->
                if (error != null) AppLog.w(TAG) { "authorization failed: ${error.localizedDescription}" }
                continuation.resume(granted)
            }
        }
        // APNs registration is what produces the token, and UIKit requires the main thread for it.
        if (granted) withContext(Dispatchers.Main) { UIApplication.sharedApplication.registerForRemoteNotifications() }
        return if (granted) PushPermission.GRANTED else PushPermission.DENIED
    }

    private companion object {
        const val TAG = "IosPush"
    }
}

class IosPushTokens : PushTokens {

    override val platform = DevicePlatform.IOS

    /** `preferredLanguages` is already BCP-47 ("vi-VN"), unlike `localeIdentifier` ("vi_VN"). */
    override val locale: String?
        get() = NSLocale.preferredLanguages.firstOrNull() as? String

    override val appVersion: String?
        get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String

    override suspend fun current(): String? {
        // Harmless when it has already been called, and needed on a launch where the permission
        // was granted in an earlier session: iOS only hands over a token after registration.
        withContext(Dispatchers.Main) { UIApplication.sharedApplication.registerForRemoteNotifications() }
        return IosPushTokenBridge.await(TOKEN_WAIT_MILLIS)
    }

    override suspend fun delete() {
        // Firebase owns the token on iOS; the app's AppDelegate deletes it and clears the bridge.
        withContext(Dispatchers.Main) { UIApplication.sharedApplication.unregisterForRemoteNotifications() }
        IosPushTokenBridge.onToken(null)
    }

    private companion object {
        const val TOKEN_WAIT_MILLIS = 5_000L
    }
}
