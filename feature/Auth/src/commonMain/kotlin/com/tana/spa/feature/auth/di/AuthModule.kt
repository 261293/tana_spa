package com.tana.spa.feature.auth.di

import com.tana.spa.feature.auth.data.local.SessionStore
import com.tana.spa.feature.auth.data.local.SessionStoreImpl
import com.tana.spa.feature.auth.data.remote.AuthRemoteApi
import com.tana.spa.feature.auth.data.remote.source.AuthRemoteApiImpl
import com.tana.spa.feature.auth.data.repository.AuthRepositoryImpl
import com.tana.spa.feature.auth.domain.repository.AuthRepository
import com.tana.spa.feature.auth.domain.usecase.GetCurrentSessionUseCase
import com.tana.spa.feature.auth.domain.usecase.LoginUseCase
import com.tana.spa.feature.auth.domain.usecase.RegisterUseCase
import com.tana.spa.feature.auth.domain.usecase.SendPhoneOtpUseCase
import com.tana.spa.feature.auth.domain.usecase.VerifyPhoneOtpUseCase
import com.tana.spa.feature.auth.presentation.manipulator.LoginViewModel
import com.tana.spa.feature.auth.presentation.manipulator.SignUpViewModel
import com.tana.spa.feature.auth.presentation.manipulator.VerifyPhoneViewModel
import com.tana.spa.shared.Constants
import com.tana.spa.shared.Platform
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule: Module = module {
    single<AuthRemoteApi> { AuthRemoteApiImpl(get(), Constants.apiBaseUrl(get<Platform>().type)) }
    single<SessionStore> { SessionStoreImpl(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    factory { LoginUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { SendPhoneOtpUseCase(get()) }
    factory { VerifyPhoneOtpUseCase(get()) }
    factory { GetCurrentSessionUseCase(get()) }
    viewModelOf(::LoginViewModel)
    viewModelOf(::SignUpViewModel)
    viewModelOf(::VerifyPhoneViewModel)
}
