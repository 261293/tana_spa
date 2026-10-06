package com.tana.spa.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.feature.auth.presentation.manipulator.LoginViewModel
import com.tana.spa.feature.auth.presentation.manipulator.SignUpViewModel
import com.tana.spa.feature.auth.presentation.manipulator.VerifyPhoneViewModel
import com.tana.spa.feature.auth.presentation.ui.LoginScreen
import com.tana.spa.feature.auth.presentation.ui.SignUpScreen
import com.tana.spa.feature.auth.presentation.ui.VerifyPhoneScreen
import org.koin.compose.viewmodel.koinViewModel

object AuthEntry : FeatureEntry {
    override val route: String = Routes.AUTH

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: LoginViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LoginScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            onNavigateHome = { navigator.replaceAll(Routes.FEED) },
            onNavigateToSignUp = { navigator.navigate(Routes.SIGNUP) },
        )
    }
}

object SignUpEntry : FeatureEntry {
    override val route: String = Routes.SIGNUP

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: SignUpViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        SignUpScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            // The account already exists, so Back must not return to the (now stale) form.
            onNavigateToVerifyPhone = { navigator.replaceAll(Routes.VERIFY_PHONE) },
            onNavigateToLogin = { navigator.replaceAll(Routes.AUTH) },
        )
    }
}

object VerifyPhoneEntry : FeatureEntry {
    override val route: String = Routes.VERIFY_PHONE

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: VerifyPhoneViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        VerifyPhoneScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            // Verified, skipped, or already verified: onboarding carries on with the welcome screen.
            onNavigateHome = { navigator.replaceAll(Routes.WELCOME) },
            onNavigateToLogin = { navigator.replaceAll(Routes.AUTH) },
        )
    }
}
