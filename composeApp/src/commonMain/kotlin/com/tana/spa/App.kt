package com.tana.spa

import androidx.compose.runtime.Composable
import com.tana.spa.common.FeaturesRegistry
import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.rememberNavigator
import com.tana.spa.core.ui.AppTheme
import com.tana.spa.core.ui.model.NavConfig
import com.tana.spa.core.ui.scafold.AppScaffold

/** Sign-in and onboarding screens: full-screen, no app bar and no bottom navigation. */
private val FULL_SCREEN_ROUTES = setOf(
    Routes.AUTH,
    Routes.SIGNUP,
    Routes.VERIFY_PHONE,
    Routes.WELCOME,
    Routes.CONSENT,
    Routes.ASSISTANT,
)

@Composable
fun App() {
    AppTheme {
        // The app opens on the login screen; it forwards to the feed itself when a valid
        // session is already stored.
        val navigator = rememberNavigator(Routes.AUTH)
        // `current` is backed by mutableStateOf, so reading it here observes navigation.
        val current = navigator.current
        val entry = FeaturesRegistry.entries.firstOrNull { it.route == current }

        if (current in FULL_SCREEN_ROUTES) {
            // Full-screen, no top/bottom app chrome during sign-in and onboarding.
            entry?.Content(navigator)
            return@AppTheme
        }

        val navItems = listOf(
            NavConfig(Routes.FEED, "Feed"),
            NavConfig(Routes.EXPLORE, "Explore"),
            NavConfig(Routes.NEW_POST, "New"),
            NavConfig(Routes.LIKES, "Likes"),
            NavConfig(Routes.PROFILE, "Profile"),
        )

        AppScaffold(
            title = "Tana SPA",
            navItems = navItems,
            selectedRoute = current,
            onSelect = { navigator.navigate(it) },
        ) {
            entry?.Content(navigator)
        }
    }
}
