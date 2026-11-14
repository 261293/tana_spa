package com.tana.spa.feature.booking.presentation

import com.tana.spa.feature.booking.domain.GuestBooking
import com.tana.spa.feature.booking.domain.Product
import com.tana.spa.shared.spa.Service

data class HomeState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val services: List<Service> = emptyList(),
    val products: List<Product> = emptyList(),
    val liveOffers: Int = 0,
    val nextVisit: GuestBooking? = null,
    /** Staff work the visits and never book one, so their home has no booking actions. */
    val canBook: Boolean = true,
)

sealed interface HomeEvent {
    data object Retry : HomeEvent
    data object Shown : HomeEvent
}

sealed interface HomeEffect
