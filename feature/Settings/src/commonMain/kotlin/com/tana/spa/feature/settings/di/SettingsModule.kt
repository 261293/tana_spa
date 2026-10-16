package com.tana.spa.feature.settings.di

import com.tana.spa.feature.settings.presentation.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** `CurrentProfile` and `SignOutAction` are bound in `composeApp`: it is the only module that sees the session. */
val settingsModule: Module = module {
    viewModelOf(::SettingsViewModel)
}
