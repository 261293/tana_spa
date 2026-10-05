package com.tana.spa.core.network

import org.koin.core.module.Module
import org.koin.dsl.module

val networkModule: Module = module {
    single { NetworkClient() }
}
