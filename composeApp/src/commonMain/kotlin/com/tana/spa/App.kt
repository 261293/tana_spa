package com.tana.spa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.tana.spa.common.FeaturesRegistry
import com.tana.spa.core.featureflags.FeatureFlagStore
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.rememberNavigator
import com.tana.spa.core.ui.AppTheme
import com.tana.spa.core.ui.model.NavConfig
import com.tana.spa.core.ui.resources.Res
import com.tana.spa.core.ui.resources.ic_nav_explore
import com.tana.spa.core.ui.resources.ic_nav_home
import com.tana.spa.core.ui.resources.ic_nav_likes
import com.tana.spa.core.ui.resources.ic_nav_new
import com.tana.spa.core.ui.resources.ic_nav_profile
import com.tana.spa.core.ui.scafold.AppScaffold
import com.tana.spa.feature.appupdate.AppUpdateGate
import org.koin.compose.koinInject

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

        val featureFlags: FeatureFlagStore = koinInject()
        LaunchedEffect(current) { featureFlags.refreshIfStale() }

        Box(Modifier.fillMaxSize()) {
            CurrentRoute(navigator, current)
            // Above every route, sign-in included: a build below the supported floor must not get
            // as far as the login form.
            AppUpdateGate.Content()
        }
    }
}

@Composable
private fun CurrentRoute(navigator: Navigator, current: String) {
    val entry = FeaturesRegistry.entries.firstOrNull { it.route == current }
    if (current in FULL_SCREEN_ROUTES) {
        // Full-screen, no top/bottom app chrome during sign-in and onboarding.
        entry?.Content(navigator)
        return
    }

    val navItems = listOf(
        NavConfig(Routes.FEED, "Home", Res.drawable.ic_nav_home),
        NavConfig(Routes.EXPLORE, "Explore", Res.drawable.ic_nav_explore),
        NavConfig(Routes.NEW_POST, "New", Res.drawable.ic_nav_new),
        NavConfig(Routes.LIKES, "Likes", Res.drawable.ic_nav_likes),
        NavConfig(Routes.PROFILE, "Profile", Res.drawable.ic_nav_profile),
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
