package com.tana.spa.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.feature.settings.presentation.SettingsViewModel
import com.tana.spa.feature.settings.presentation.ui.SettingsScreen
import org.koin.compose.viewmodel.koinViewModel

object SettingsEntry : FeatureEntry {
    override val route: String = Routes.SETTINGS

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: SettingsViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        SettingsScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            // Back must not return to the signed-in app after signing out.
            onSignedOut = { navigator.replaceAll(Routes.AUTH) },
        )
    }
}
