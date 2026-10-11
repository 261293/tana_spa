package com.tana.spa.feature.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.feature.assistant.presentation.manipulator.ChatViewModel
import com.tana.spa.feature.assistant.presentation.ui.ChatScreen
import com.tana.spa.feature.assistant.presentation.ui.ConsentScreen
import com.tana.spa.feature.assistant.presentation.ui.WelcomeScreen
import org.koin.compose.viewmodel.koinViewModel

object WelcomeEntry : FeatureEntry {
    override val route: String = Routes.WELCOME

    @Composable
    override fun Content(navigator: Navigator) {
        // Pure UI state (a tick box), so it lives here rather than in a ViewModel.
        var accepted by rememberSaveable { mutableStateOf(false) }
        WelcomeScreen(
            accepted = accepted,
            onAcceptedChange = { accepted = it },
            onContinue = { navigator.navigate(Routes.CONSENT) },
            onClose = { navigator.replaceAll(Routes.FEED) },
        )
    }
}

object ConsentEntry : FeatureEntry {
    override val route: String = Routes.CONSENT

    @Composable
    override fun Content(navigator: Navigator) {
        var agreed by rememberSaveable { mutableStateOf(false) }
        ConsentScreen(
            agreed = agreed,
            onAgreedChange = { agreed = it },
            onBack = { navigator.back() },
            // Consent given: the chat replaces the stack so Back cannot return to the consent form.
            onContinue = { navigator.replaceAll(Routes.ASSISTANT) },
        )
    }
}

object AssistantEntry : FeatureEntry {
    override val route: String = Routes.ASSISTANT

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: ChatViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        ChatScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            onNavigateHome = { navigator.replaceAll(Routes.FEED) },
        )
    }
}
