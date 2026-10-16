package com.tana.spa.feature.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.withArg
import com.tana.spa.feature.notifications.presentation.NotificationsViewModel
import com.tana.spa.feature.notifications.presentation.ui.NotificationsScreen
import org.koin.compose.viewmodel.koinViewModel

object NotificationsEntry : FeatureEntry {
    override val route: String = Routes.NOTIFICATIONS

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: NotificationsViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        NotificationsScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            // A reminder or a decision is about one visit, so it opens the guest's list on it.
            onOpenBooking = { navigator.navigate(Routes.MY_BOOKINGS.withArg(Routes.ARG_BOOKING, it)) },
        )
    }
}
