package com.tana.spa.di

import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.ui.SelectedLanguage
import com.tana.spa.feature.settings.domain.LanguageChoice
import com.tana.spa.shared.getPlatform
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/** Starts Koin with all app modules. Each platform entry point calls this once. */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    val koin = startKoin {
        appDeclaration()
        modules(appModules())
    }.koin
    // Before the first composition: the app draws itself in this language, whatever the device is
    // set to, so a Vietnamese spa reads Vietnamese on an English phone.
    SelectedLanguage.value = koin.get<LanguageChoice>().load()
    AppLog.i("App") { "started platform=${getPlatform().name} language=${SelectedLanguage.value.tag}" }
}
