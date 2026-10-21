package com.tana.spa.feature.booking.di

import com.tana.spa.feature.booking.data.GuestBookingRepositoryImpl
import com.tana.spa.feature.booking.data.SpaCatalogRepositoryImpl
import com.tana.spa.feature.booking.domain.GuestBookingRepository
import com.tana.spa.feature.booking.domain.SpaCatalogRepository
import com.tana.spa.feature.booking.presentation.BookingViewModel
import com.tana.spa.feature.booking.presentation.BookingsViewModel
import com.tana.spa.feature.booking.presentation.CurrentVisit
import com.tana.spa.feature.booking.presentation.ServiceDetailViewModel
import com.tana.spa.shared.Constants
import com.tana.spa.shared.Platform
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val bookingModule: Module = module {
    single<SpaCatalogRepository> { SpaCatalogRepositoryImpl(get(), Constants.apiBaseUrl(get<Platform>().type)) }
    single<GuestBookingRepository> { GuestBookingRepositoryImpl(get(), Constants.apiBaseUrl(get<Platform>().type), get()) }

    // Held for the whole app, not a screen: the strip that reads it outlives every screen below it.
    single { CurrentVisit(get(), get()) }

    viewModelOf(::BookingViewModel)
    viewModelOf(::BookingsViewModel)
    viewModelOf(::ServiceDetailViewModel)
}
