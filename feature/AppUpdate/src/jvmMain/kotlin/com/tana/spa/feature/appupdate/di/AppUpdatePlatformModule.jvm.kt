package com.tana.spa.feature.appupdate.di

import com.tana.spa.core.logging.AppLog
import com.tana.spa.feature.appupdate.domain.InstalledAppVersion
import com.tana.spa.feature.appupdate.domain.StoreLauncher
import org.koin.core.module.Module
import org.koin.dsl.module

/** No app store on this target, so a null version keeps the gate inert. */
actual val appUpdatePlatformModule: Module = module {
    single<InstalledAppVersion> { InstalledAppVersion { null } }

    single<StoreLauncher> {
        StoreLauncher { url -> AppLog.i("AppUpdate") { "no app store on this platform, ignoring $url" } }
    }
}
