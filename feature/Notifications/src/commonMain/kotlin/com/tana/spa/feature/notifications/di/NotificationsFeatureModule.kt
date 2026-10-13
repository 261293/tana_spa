package com.tana.spa.feature.notifications.di

import com.tana.spa.feature.notifications.data.NotificationsRepositoryImpl
import com.tana.spa.feature.notifications.domain.NotificationsRepository
import com.tana.spa.feature.notifications.presentation.NotificationsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The API client, the badge and the push registrar come from `core:notifications`. */
val notificationsModule: Module = module {
    single<NotificationsRepository> { NotificationsRepositoryImpl(get(), get()) }

    viewModelOf(::NotificationsViewModel)
}
