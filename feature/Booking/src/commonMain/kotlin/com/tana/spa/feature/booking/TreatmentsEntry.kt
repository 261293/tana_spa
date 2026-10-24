package com.tana.spa.feature.booking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.withArg
import com.tana.spa.feature.booking.presentation.TreatmentsEvent
import com.tana.spa.feature.booking.presentation.TreatmentsViewModel
import com.tana.spa.feature.booking.presentation.ui.TreatmentsScreen
import org.koin.compose.viewmodel.koinViewModel

object TreatmentsEntry : FeatureEntry {
    override val route: String = Routes.TREATMENTS

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: TreatmentsViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.onEvent(TreatmentsEvent.Opened) }
        TreatmentsScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            onOpenTreatment = { navigator.navigate(Routes.SERVICE_DETAIL.withArg(Routes.ARG_SERVICE, it)) },
        )
    }
}
