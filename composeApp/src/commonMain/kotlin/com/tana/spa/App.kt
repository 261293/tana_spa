package com.tana.spa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.common.FeaturesRegistry
import com.tana.spa.core.featureflags.FeatureFlagStore
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.rememberNavigator
import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.notifications.NotificationBadge
import com.tana.spa.core.notifications.PushRegistrar
import com.tana.spa.core.ui.AppTheme
import com.tana.spa.core.ui.components.SpaNotificationBell
import com.tana.spa.core.ui.model.NavConfig
import com.tana.spa.core.ui.resources.Res
import com.tana.spa.core.ui.resources.bell_notifications
import com.tana.spa.core.ui.resources.bell_notifications_unread
import com.tana.spa.core.ui.resources.ic_bell
import com.tana.spa.core.ui.resources.ic_nav_explore
import com.tana.spa.core.ui.resources.ic_nav_home
import com.tana.spa.core.ui.resources.ic_nav_likes
import com.tana.spa.core.ui.resources.ic_nav_new
import com.tana.spa.core.ui.resources.ic_nav_profile
import com.tana.spa.core.ui.scafold.AppScaffold
import com.tana.spa.feature.appupdate.AppUpdateGate
import com.tana.spa.shared.featureflags.FeatureFlag
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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
        val flagStates by featureFlags.flags.collectAsStateWithLifecycle()
        val notificationsEnabled =
            flagStates[FeatureFlag.NOTIFICATIONS] ?: FeatureFlag.NOTIFICATIONS.defaultEnabled

        PushRegistration(current, notificationsEnabled)

        Box(Modifier.fillMaxSize()) {
            CurrentRoute(navigator, current, notificationsEnabled)
            // Above every route, sign-in included: a build below the supported floor must not get
            // as far as the login form.
            AppUpdateGate.Content()
        }
    }
}

/**
 * Keyed on the access token, so a sign-in re-registers this device: the push token belongs to the
 * install, and the server has to be told it now belongs to a different account.
 */
@Composable
private fun PushRegistration(current: String, notificationsEnabled: Boolean) {
    val registrar: PushRegistrar = koinInject()
    val badge: NotificationBadge = koinInject()
    val sessions: AccessTokenSource = koinInject()
    val session = sessions.accessToken()

    LaunchedEffect(session) {
        badge.refresh()
        // force: an unchanged token still has to move to whoever just signed in.
        if (session != null) registrar.registerIfPossible(force = true)
    }
    // A push that arrives while the app is open does not update the bell on its own, so the count
    // is re-read as the user moves around. Costs nothing while the feature is switched off.
    LaunchedEffect(current, notificationsEnabled) {
        if (notificationsEnabled) badge.refresh()
    }
}

@Composable
private fun CurrentRoute(navigator: Navigator, current: String, notificationsEnabled: Boolean) {
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
        topBarActions = {
            if (notificationsEnabled && current != Routes.NOTIFICATIONS) {
                NotificationBell(onOpen = { navigator.navigate(Routes.NOTIFICATIONS) })
            }
        },
    ) {
        entry?.Content(navigator)
    }
}

@Composable
private fun NotificationBell(onOpen: () -> Unit) {
    val badge: NotificationBadge = koinInject()
    val unread by badge.unreadCount.collectAsStateWithLifecycle()
    SpaNotificationBell(
        icon = painterResource(Res.drawable.ic_bell),
        contentDescription = if (unread > 0) {
            stringResource(Res.string.bell_notifications_unread, unread)
        } else {
            stringResource(Res.string.bell_notifications)
        },
        unreadCount = unread,
        onClick = onOpen,
    )
}
