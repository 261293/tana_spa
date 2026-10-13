package com.tana.spa.core.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.tana.spa.core.logging.AppLog
import com.tana.spa.shared.notifications.DevicePlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Shows the `POST_NOTIFICATIONS` prompt. The system dialog needs an Activity, which no `core`
 * module has, so the app shell binds this (see `androidApp`'s `MainActivity`). Without it the
 * permission can only be read, never asked for.
 */
fun interface PostNotificationsPrompt {
    suspend fun requestPostNotifications(): Boolean
}

/**
 * [NotificationManagerCompat.areNotificationsEnabled] is the state that actually decides whether a
 * push is shown: it covers both the runtime permission and the user switching notifications off in
 * Settings, which the permission check alone does not.
 */
class AndroidPushPermissions(private val context: Context, private val prompt: PostNotificationsPrompt? = null) : PushPermissions {

    override suspend fun status(): PushPermission = when {
        NotificationManagerCompat.from(context).areNotificationsEnabled() -> PushPermission.GRANTED

        // Below Android 13 there is no runtime permission, so "not enabled" can only mean the
        // user turned them off in Settings, and no prompt would change that.
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> PushPermission.DENIED

        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED -> PushPermission.DENIED

        else -> PushPermission.NOT_DETERMINED
    }

    override suspend fun request(): PushPermission {
        val current = status()
        if (current != PushPermission.NOT_DETERMINED) return current
        val granted = prompt?.requestPostNotifications() ?: run {
            AppLog.w(TAG) { "no Activity prompt is bound, cannot ask for POST_NOTIFICATIONS" }
            return current
        }
        return if (granted) PushPermission.GRANTED else PushPermission.DENIED
    }

    private companion object {
        const val TAG = "AndroidPush"
    }
}

/**
 * The FCM registration token. Every call degrades to null when Firebase is not initialised — an
 * app built without `google-services.json` is a working app without push, not a crash.
 */
class AndroidPushTokens(private val context: Context) : PushTokens {

    override val platform = DevicePlatform.ANDROID

    override val locale: String get() = Locale.getDefault().toLanguageTag()

    override val appVersion: String?
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()

    override suspend fun current(): String? = await("token") { FirebaseMessaging.getInstance().token }

    override suspend fun delete() {
        await("deleteToken") { FirebaseMessaging.getInstance().deleteToken() }
    }

    private suspend fun <T> await(what: String, start: () -> Task<T>): T? = suspendCancellableCoroutine { continuation ->
        val task = try {
            start()
        } catch (e: Exception) {
            // IllegalStateException when no FirebaseApp was initialised, i.e. no google-services.json.
            AppLog.w(TAG, e) { "FCM $what unavailable (${e::class.simpleName})" }
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        task.addOnCompleteListener { completed ->
            if (completed.isSuccessful) {
                continuation.resume(completed.result)
            } else {
                AppLog.w(TAG, completed.exception) { "FCM $what failed" }
                continuation.resume(null)
            }
        }
    }

    private companion object {
        const val TAG = "AndroidPush"
    }
}
