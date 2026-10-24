package com.tana.spa.feature.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.feature.feed.presentation.manipulator.FeedViewmodel
import com.tana.spa.feature.feed.presentation.ui.FeedScreen
import org.koin.compose.viewmodel.koinViewModel

object FeedEntry : FeatureEntry {
    override val route: String = Routes.FEED

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: FeedViewmodel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        FeedScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            // The welcome screen opens the assistant flow; going straight to the chat would skip consent.
            onOpenAssistant = { navigator.navigate(Routes.WELCOME) },
            onBook = { navigator.navigate(Routes.BOOKING) },
            onExploreTreatments = { navigator.navigate(Routes.TREATMENTS) },
        )
    }
}
