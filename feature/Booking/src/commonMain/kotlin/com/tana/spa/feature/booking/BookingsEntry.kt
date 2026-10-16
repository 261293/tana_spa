package com.tana.spa.feature.booking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.routeArg
import com.tana.spa.feature.booking.presentation.BookingsEvent
import com.tana.spa.feature.booking.presentation.BookingsViewModel
import com.tana.spa.feature.booking.presentation.ui.BookingsScreen
import org.koin.compose.viewmodel.koinViewModel

object BookingsEntry : FeatureEntry {
    override val route: String = Routes.MY_BOOKINGS

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: BookingsViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val opened = navigator.current.routeArg(Routes.ARG_BOOKING)
        LaunchedEffect(opened) { viewModel.onEvent(BookingsEvent.Opened(opened)) }
        BookingsScreen(state = state, effects = viewModel.effects, onEvent = viewModel::onEvent)
    }
}
