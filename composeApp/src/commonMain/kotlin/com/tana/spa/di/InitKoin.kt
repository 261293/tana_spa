package com.tana.spa.di

import com.tana.spa.core.logging.AppLog
import com.tana.spa.shared.getPlatform
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/** Starts Koin with all app modules. Each platform entry point calls this once. */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(appModules())
    }
    AppLog.i("App") { "started platform=${getPlatform().name}" }
}
