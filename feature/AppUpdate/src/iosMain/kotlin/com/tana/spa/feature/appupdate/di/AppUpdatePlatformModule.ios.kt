package com.tana.spa.feature.appupdate.di

import com.tana.spa.feature.appupdate.domain.InstalledAppVersion
import com.tana.spa.feature.appupdate.domain.StoreLauncher
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual val appUpdatePlatformModule: Module = module {
    single<InstalledAppVersion> {
        // CFBundleShortVersionString is MARKETING_VERSION, the version the App Store shows.
        InstalledAppVersion {
            NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        }
    }

    single<StoreLauncher> {
        StoreLauncher { url ->
            NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it) }
        }
    }
}
