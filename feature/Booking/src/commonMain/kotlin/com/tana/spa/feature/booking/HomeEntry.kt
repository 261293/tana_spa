package com.tana.spa.feature.booking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.withArg
import com.tana.spa.feature.booking.presentation.HomeEvent
import com.tana.spa.feature.booking.presentation.HomeViewModel
import com.tana.spa.feature.booking.presentation.ui.HomeScreen
import org.koin.compose.viewmodel.koinViewModel

object HomeEntry : FeatureEntry {
    override val route: String = Routes.HOME

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: HomeViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.onEvent(HomeEvent.Shown) }
        HomeScreen(
            state = state,
            onEvent = viewModel::onEvent,
            onBookService = { navigator.navigate(Routes.BOOKING.withArg(Routes.ARG_SERVICE, it)) },
            onOpenOffers = { navigator.navigate(Routes.PROMOTIONS) },
            onOpenVisit = { navigator.navigate(Routes.MY_BOOKINGS.withArg(Routes.ARG_BOOKING, it)) },
        )
    }
}
