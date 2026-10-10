package com.tana.spa.feature.appupdate.di

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tana.spa.feature.appupdate.domain.InstalledAppVersion
import com.tana.spa.feature.appupdate.domain.StoreLauncher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appUpdatePlatformModule: Module = module {
    single<InstalledAppVersion> {
        val context: Context = androidContext()
        // A version the package manager will not give us leaves the gate inert, which is the
        // same outcome as being offline.
        InstalledAppVersion {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        }
    }

    single<StoreLauncher> {
        val context: Context = androidContext()
        StoreLauncher { url ->
            // Started from an application context, so the intent needs a task of its own.
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
