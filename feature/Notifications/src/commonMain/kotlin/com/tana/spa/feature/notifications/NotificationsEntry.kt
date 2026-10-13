package com.tana.spa.feature.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.feature.notifications.presentation.NotificationsViewModel
import com.tana.spa.feature.notifications.presentation.ui.NotificationsScreen
import org.koin.compose.viewmodel.koinViewModel

object NotificationsEntry : FeatureEntry {
    override val route: String = Routes.NOTIFICATIONS

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: NotificationsViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        // Tapping an entry marks it read; nothing deep-links yet, so the payload in
        // NotificationItem.data is carried but not routed on.
        NotificationsScreen(state = state, effects = viewModel.effects, onEvent = viewModel::onEvent)
    }
}
