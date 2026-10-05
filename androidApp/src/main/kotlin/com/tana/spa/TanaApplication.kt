package com.tana.spa

import android.app.Application
import com.tana.spa.di.initKoin
import org.koin.android.ext.koin.androidContext

class TanaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@TanaApplication)
        }
    }
}
