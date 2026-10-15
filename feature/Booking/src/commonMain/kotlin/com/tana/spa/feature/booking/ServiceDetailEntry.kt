package com.tana.spa.feature.booking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.routeArg
import com.tana.spa.feature.booking.presentation.ServiceDetailEvent
import com.tana.spa.feature.booking.presentation.ServiceDetailViewModel
import com.tana.spa.feature.booking.presentation.ui.ServiceDetailScreen
import org.koin.compose.viewmodel.koinViewModel

object ServiceDetailEntry : FeatureEntry {
    override val route: String = Routes.SERVICE_DETAIL

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: ServiceDetailViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val serviceId = navigator.current.routeArg(Routes.ARG_SERVICE)
        LaunchedEffect(serviceId) { viewModel.onEvent(ServiceDetailEvent.Opened(serviceId)) }
        ServiceDetailScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            // The list chose the treatment on the way in, so both Back and Book return to the flow.
            onBack = navigator::back,
        )
    }
}
