package com.tana.spa

import android.app.Application
import android.content.pm.ApplicationInfo
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.logging.LogLevel
import com.tana.spa.di.initKoin
import org.koin.android.ext.koin.androidContext

class TanaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Chatty DEBUG lines (every ViewModel event) are for developers; release builds log INFO and up.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) AppLog.minLevel = LogLevel.DEBUG
        initKoin {
            androidContext(this@TanaApplication)
        }
    }
}
