package com.tana.spa.feature.appupdate.di

import com.tana.spa.feature.appupdate.data.AppUpdateRemoteApi
import com.tana.spa.feature.appupdate.data.AppUpdateRemoteApiImpl
import com.tana.spa.feature.appupdate.presentation.AppUpdateViewModel
import com.tana.spa.shared.Constants
import com.tana.spa.shared.Platform
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appUpdateModule: Module = module {
    includes(appUpdatePlatformModule)

    single<AppUpdateRemoteApi> { AppUpdateRemoteApiImpl(get(), Constants.apiBaseUrl(get<Platform>().type)) }

    viewModelOf(::AppUpdateViewModel)
}

/** Each target supplies its own installed version and store launcher. */
expect val appUpdatePlatformModule: Module
