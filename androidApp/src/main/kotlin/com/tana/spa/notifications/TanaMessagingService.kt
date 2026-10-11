package com.tana.spa.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tana.spa.R
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.notifications.PushRegistrar
import com.tana.spa.shared.notifications.PushChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Declared in `AndroidManifest.xml`. Firebase posts a message's `notification` block itself while
 * the app is in the background, so this class exists for the two cases it does not cover: a token
 * the provider rotated, and a message that arrives while the app is in the foreground.
 */
class TanaMessagingService :
    FirebaseMessagingService(),
    KoinComponent {

    private val registrar: PushRegistrar by inject()

    // The service can be destroyed as soon as the callback returns, so this scope deliberately
    // outlives it rather than being cancelled in onDestroy.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        AppLog.i(TAG) { "FCM rotated this device's token" }
        // force: the token in the cache is the old one, and the server has never seen this one.
        scope.launch { registrar.registerIfPossible(force = true) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification
        val title = notification?.title ?: message.data[DATA_TITLE]
        val body = notification?.body ?: message.data[DATA_BODY]
        if (title == null && body == null) {
            AppLog.d(TAG) { "ignoring a data-only message with nothing to show" }
            return
        }
        // Never the payload: a notification body can quote an order or a phone number.
        AppLog.i(TAG) { "showing a foreground notification" }
        show(title, body, message.data[DATA_TYPE])
    }

    private fun show(title: String?, body: String?, type: String?) {
        ensureChannel(this)
        val builder = NotificationCompat.Builder(this, PushChannels.DEFAULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        // One notification per kind, so a stream of order updates does not bury everything else.
        val tag = type ?: PushChannels.DEFAULT
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) manager.notify(tag, NOTIFICATION_ID, builder.build())
    }

    private companion object {
        const val TAG = "AndroidPush"
        const val DATA_TITLE = "title"
        const val DATA_BODY = "body"
        const val DATA_TYPE = "type"
        const val NOTIFICATION_ID = 1
    }
}

/**
 * Android 8+ drops a notification whose channel does not exist, and the server names
 * [PushChannels.DEFAULT] in every message. Creating it again is a no-op, so this runs at launch
 * and before each post.
 */
fun ensureChannel(context: Context) {
    val channel = NotificationChannel(
        PushChannels.DEFAULT,
        context.getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.notification_channel_description)
    }
    context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
}
