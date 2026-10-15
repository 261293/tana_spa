package com.tana.spa.feature.admin.di

import com.tana.spa.feature.admin.data.DeskRepositoryImpl
import com.tana.spa.feature.admin.domain.DeskRepository
import com.tana.spa.feature.admin.presentation.AdminCodesViewModel
import com.tana.spa.feature.admin.presentation.AdminMenuViewModel
import com.tana.spa.feature.admin.presentation.AdminRequestsViewModel
import com.tana.spa.shared.Constants
import com.tana.spa.shared.Platform
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val adminModule: Module = module {
    single<DeskRepository> { DeskRepositoryImpl(get(), Constants.apiBaseUrl(get<Platform>().type), get()) }

    viewModelOf(::AdminRequestsViewModel)
    viewModelOf(::AdminMenuViewModel)
    viewModelOf(::AdminCodesViewModel)
}
