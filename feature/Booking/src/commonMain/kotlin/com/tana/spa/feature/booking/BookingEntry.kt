package com.tana.spa.feature.booking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.routeArg
import com.tana.spa.core.navigation.withArg
import com.tana.spa.feature.booking.presentation.BookingEvent
import com.tana.spa.feature.booking.presentation.BookingViewModel
import com.tana.spa.feature.booking.presentation.ui.BookingScreen
import org.koin.compose.viewmodel.koinViewModel

object BookingEntry : FeatureEntry {
    override val route: String = Routes.BOOKING

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: BookingViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val offeredCode = navigator.current.routeArg(Routes.ARG_CODE)
        LaunchedEffect(offeredCode) {
            offeredCode?.let { viewModel.onEvent(BookingEvent.CodeOffered(it)) }
        }
        BookingScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            onOpenNotifications = { navigator.navigate(Routes.NOTIFICATIONS) },
            onOpenBookings = { navigator.navigate(Routes.MY_BOOKINGS) },
            onOpenService = { navigator.navigate(Routes.SERVICE_DETAIL.withArg(Routes.ARG_SERVICE, it)) },
        )
    }
}
