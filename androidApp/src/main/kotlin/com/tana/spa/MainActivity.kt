package com.tana.spa

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tana.spa.notifications.NotificationPermissionRequests
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {

    /** One prompt at a time; the launcher's callback is the only thing that completes it. */
    private var pendingPermission: CompletableDeferred<Boolean>? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingPermission?.complete(granted)
            pendingPermission = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only an Activity can show the notification permission dialog, so the app's push code
        // borrows this one for as long as it is alive.
        NotificationPermissionRequests.attach(::requestNotificationPermission)
        setContent {
            App()
        }
    }

    override fun onDestroy() {
        NotificationPermissionRequests.detach()
        pendingPermission?.complete(false)
        pendingPermission = null
        super.onDestroy()
    }

    private suspend fun requestNotificationPermission(): Boolean {
        // There is no runtime permission before Android 13: notifications are on unless the user
        // switched them off in Settings, which no dialog can undo.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        pendingPermission?.let { return it.await() }
        val pending = CompletableDeferred<Boolean>()
        pendingPermission = pending
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        return pending.await()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
