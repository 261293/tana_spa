package com.tana.spa.shared.di

import com.tana.spa.shared.Greeting
import com.tana.spa.shared.getPlatform
import com.tana.spa.shared.helpers.DefaultDispatcherProvider
import com.tana.spa.shared.helpers.DispatcherProvider
import com.tana.spa.shared.helpers.Logger
import com.tana.spa.shared.helpers.PrintLogger
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedModule: Module = module {
    single { getPlatform() }
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<Logger> { PrintLogger() }
    single { Greeting() }
}

/** Per-platform Koin bindings, contributed by each target's `Modules.<platform>.kt`. */
expect val platformModule: Module
