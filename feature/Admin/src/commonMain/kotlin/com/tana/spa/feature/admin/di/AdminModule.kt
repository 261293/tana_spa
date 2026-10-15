package com.tana.spa.feature.admin.di

import com.tana.spa.feature.admin.presentation.AdminCodesViewModel
import com.tana.spa.feature.admin.presentation.AdminRequestsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val adminModule: Module = module {
    viewModelOf(::AdminRequestsViewModel)
    viewModelOf(::AdminCodesViewModel)
}
