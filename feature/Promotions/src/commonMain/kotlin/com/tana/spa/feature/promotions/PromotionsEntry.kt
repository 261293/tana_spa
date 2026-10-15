package com.tana.spa.feature.promotions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.withArg
import com.tana.spa.feature.promotions.presentation.PromotionsViewModel
import com.tana.spa.feature.promotions.presentation.ui.PromotionsScreen
import org.koin.compose.viewmodel.koinViewModel

object PromotionsEntry : FeatureEntry {
    override val route: String = Routes.PROMOTIONS

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: PromotionsViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        PromotionsScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            // The booking screen picks the code up from the route and applies it.
            onOpenBooking = { code -> navigator.navigate(Routes.BOOKING.withArg(Routes.ARG_CODE, code)) },
        )
    }
}
