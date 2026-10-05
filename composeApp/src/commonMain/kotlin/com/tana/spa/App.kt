package com.tana.spa

import androidx.compose.runtime.Composable
import com.tana.spa.common.FeaturesRegistry
import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.rememberNavigator
import com.tana.spa.core.ui.AppTheme
import com.tana.spa.core.ui.model.NavConfig
import com.tana.spa.core.ui.scafold.AppScaffold

@Composable
fun App() {
    AppTheme {
        val navigator = rememberNavigator(Routes.FEED)
        // `current` is backed by mutableStateOf, so reading it here observes navigation.
        val current = navigator.current

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
            FeaturesRegistry.entries
                .firstOrNull { it.route == current }
                ?.Content(navigator)
        }
    }
}
