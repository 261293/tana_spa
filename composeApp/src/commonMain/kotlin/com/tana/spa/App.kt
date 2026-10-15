package com.tana.spa

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.common.FeaturesRegistry
import com.tana.spa.core.featureflags.FeatureFlagStore
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.rememberNavigator
import com.tana.spa.core.navigation.routeName
import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.notifications.NotificationBadge
import com.tana.spa.core.notifications.PushRegistrar
import com.tana.spa.core.ui.AppTheme
import com.tana.spa.core.ui.ProvideSpaWindowSize
import com.tana.spa.core.ui.components.SpaAvatar
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.model.NavConfig
import com.tana.spa.core.ui.scafold.AppScaffold
import com.tana.spa.feature.appupdate.AppUpdateGate
import com.tana.spa.feature.auth.data.local.SessionStore
import com.tana.spa.feature.auth.domain.model.UserRoleName
import com.tana.spa.shared.featureflags.FeatureFlag
import org.koin.compose.koinInject

/**
 * Sign-in, onboarding and the staff console: full-screen, without the guest app bar or tab bar.
 */
private val FULL_SCREEN_ROUTES = setOf(
    Routes.AUTH,
    Routes.SIGNUP,
    Routes.VERIFY_PHONE,
    Routes.WELCOME,
    Routes.CONSENT,
    Routes.ASSISTANT,
    Routes.ADMIN_REQUESTS,
    Routes.ADMIN_CODES,
)

/** Screens the Book destination owns: reaching one does not leave the booking flow. */
private val BOOKING_FLOW_ROUTES = setOf(Routes.SERVICE_DETAIL, Routes.MY_BOOKINGS)

@Composable
fun App() {
    AppTheme {
        // The app opens on the login screen; it forwards to the booking screen itself when a
        // valid session is already stored.
        val navigator = rememberNavigator(Routes.AUTH)
        // `current` is backed by mutableStateOf, so reading it here observes navigation.
        val current = navigator.current

        val featureFlags: FeatureFlagStore = koinInject()
        LaunchedEffect(current) { featureFlags.refreshIfStale() }
        val flagStates by featureFlags.flags.collectAsStateWithLifecycle()
        val notificationsEnabled =
            flagStates[FeatureFlag.NOTIFICATIONS] ?: FeatureFlag.NOTIFICATIONS.defaultEnabled

        PushRegistration(current, notificationsEnabled)

        // Measured once, here: every screen below reads the same window size class.
        ProvideSpaWindowSize(Modifier.fillMaxSize()) {
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
    // A push that arrives while the app is open does not update the badge on its own, so the count
    // is re-read as the user moves around. Costs nothing while the feature is switched off.
    LaunchedEffect(current, notificationsEnabled) {
        if (notificationsEnabled) badge.refresh()
    }
}

@Composable
private fun CurrentRoute(navigator: Navigator, current: String, notificationsEnabled: Boolean) {
    // A route may carry an argument ("booking?code=JADE20"); entries are registered under the name.
    val route = current.routeName()
    val entry = FeaturesRegistry.entries.firstOrNull { it.route == route }
    // Keeps each screen's saveable state (scroll position above all) while it is off screen, so
    // coming back from a detail page lands where the guest left the list.
    val screenState = rememberSaveableStateHolder()
    if (route in FULL_SCREEN_ROUTES) {
        screenState.SaveableStateProvider(route) { entry?.Content(navigator) }
        return
    }

    val badge: NotificationBadge = koinInject()
    val unread by badge.unreadCount.collectAsStateWithLifecycle()

    // Read per route change rather than observed: a sign-in or sign-out always navigates.
    val sessions: SessionStore = koinInject()
    val owner = remember(current) { sessions.load()?.user?.role == UserRoleName.OWNER }

    val navItems = buildList {
        add(NavConfig(Routes.BOOKING, "Book", SpaIcon.CalendarPlus))
        add(NavConfig(Routes.NOTIFICATIONS, "Alerts", SpaIcon.Bell, badgeCount = if (notificationsEnabled) unread else 0))
        add(NavConfig(Routes.PROMOTIONS, "Offers", SpaIcon.Tag))
        // The spa's own console. The server refuses its calls for every other role; this only
        // keeps the destination out of a guest's way.
        if (owner) add(NavConfig(Routes.ADMIN_REQUESTS, "Desk", SpaIcon.Calendar))
        add(NavConfig(Routes.SETTINGS, "Settings", SpaIcon.Gear))
    }

    AppScaffold(
        navItems = navItems,
        // A treatment's detail page and the guest's own visits belong to the booking flow, so
        // Book stays the lit destination on both.
        selectedRoute = if (route in BOOKING_FLOW_ROUTES) Routes.BOOKING else route,
        onSelect = { navigator.navigate(it) },
        topBarActions = {
            SpaAvatar(
                initials = "AL",
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clickable(role = Role.Button) { navigator.navigate(Routes.SETTINGS) },
            )
        },
    ) {
        screenState.SaveableStateProvider(route) { entry?.Content(navigator) }
    }
}
