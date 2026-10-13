package com.tana.spa

import android.app.Application
import android.content.pm.ApplicationInfo
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.logging.LogLevel
import com.tana.spa.core.notifications.PostNotificationsPrompt
import com.tana.spa.di.initKoin
import com.tana.spa.notifications.NotificationPermissionRequests
import com.tana.spa.notifications.ensureChannel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class TanaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Chatty DEBUG lines (every ViewModel event) are for developers; release builds log INFO and up.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) AppLog.minLevel = LogLevel.DEBUG
        // Before the first push can arrive: Android 8+ drops a notification whose channel is
        // missing, and a message can be delivered while the app has never been opened.
        ensureChannel(this)
        initKoin {
            androidContext(this@TanaApplication)
            // The Activity-backed permission dialog, which no library module can reach.
            modules(module { single<PostNotificationsPrompt> { NotificationPermissionRequests } })
        }
    }
}
