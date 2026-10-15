package com.tana.spa.feature.booking.di

import com.tana.spa.feature.booking.presentation.BookingViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val bookingModule: Module = module {
    viewModelOf(::BookingViewModel)
}
