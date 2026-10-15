package com.tana.spa.feature.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.feature.admin.presentation.AdminCodesViewModel
import com.tana.spa.feature.admin.presentation.AdminRequestsViewModel
import com.tana.spa.feature.admin.presentation.ui.AdminCodesScreen
import com.tana.spa.feature.admin.presentation.ui.AdminRequestsScreen
import org.koin.compose.viewmodel.koinViewModel

object AdminRequestsEntry : FeatureEntry {
    override val route: String = Routes.ADMIN_REQUESTS

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: AdminRequestsViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        AdminRequestsScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            onOpenCodes = { navigator.navigate(Routes.ADMIN_CODES) },
            onOpenGuestApp = { navigator.navigate(Routes.BOOKING) },
        )
    }
}

object AdminCodesEntry : FeatureEntry {
    override val route: String = Routes.ADMIN_CODES

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: AdminCodesViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        // The queue badge belongs to the other screen's view model, which is scoped to that screen;
        // the requests count is read from the same catalogue it starts from.
        val requests: AdminRequestsViewModel = koinViewModel()
        val requestsState by requests.state.collectAsStateWithLifecycle()
        AdminCodesScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            pendingRequests = requestsState.pending.size,
            onOpenRequests = { navigator.navigate(Routes.ADMIN_REQUESTS) },
            onOpenGuestApp = { navigator.navigate(Routes.BOOKING) },
        )
    }
}
