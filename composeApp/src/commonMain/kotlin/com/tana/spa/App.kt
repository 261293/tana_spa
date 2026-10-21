package com.tana.spa

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
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
import com.tana.spa.core.navigation.withArg
import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.notifications.NotificationBadge
import com.tana.spa.core.notifications.PushRegistrar
import com.tana.spa.core.ui.AppTheme
import com.tana.spa.core.ui.ProvideSpaWindowSize
import com.tana.spa.core.ui.SpaMotion
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaAvatar
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaSplash
import com.tana.spa.core.ui.model.NavConfig
import com.tana.spa.core.ui.resources.Res
import com.tana.spa.core.ui.resources.nav_alerts
import com.tana.spa.core.ui.resources.nav_book
import com.tana.spa.core.ui.resources.nav_desk
import com.tana.spa.core.ui.resources.nav_offers
import com.tana.spa.core.ui.resources.nav_settings
import com.tana.spa.core.ui.resources.nav_visits
import com.tana.spa.core.ui.scafold.AppScaffold
import com.tana.spa.feature.appupdate.AppUpdateGate
import com.tana.spa.feature.auth.data.local.SessionStore
import com.tana.spa.feature.auth.domain.model.UserRoleName
import com.tana.spa.feature.booking.presentation.CurrentVisit
import com.tana.spa.feature.booking.presentation.ui.CurrentVisitStrip
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.spa.initialsOf
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
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
    Routes.ADMIN_MENU,
    Routes.ADMIN_CODES,
)

/** Screens the Book destination owns: reaching one does not leave the booking flow. */
private val BOOKING_FLOW_ROUTES = setOf(Routes.SERVICE_DETAIL, Routes.MY_BOOKINGS)

/**
 * Where the current-visit strip stays out of the way: the booking screen has its own strip for the
 * booking being composed, and the visits list is where the strip would send them anyway.
 */
private val NO_VISIT_STRIP_ROUTES = setOf(Routes.BOOKING, Routes.MY_BOOKINGS)

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
        CurrentVisitRefresh(current)

        // Measured once, here: every screen below reads the same window size class.
        ProvideSpaWindowSize(Modifier.fillMaxSize()) {
            // Below the navigator on purpose: changing language composes this subtree again, and
            // the back stack has to survive that rather than drop the guest back at sign-in.
            AppLocale {
                CurrentRoute(navigator, current, notificationsEnabled)
                // Above every route, sign-in included: a build below the supported floor must not
                // get as far as the login form.
                AppUpdateGate.Content()
            }
            // Outside AppLocale, whose key() would bring the splash back on every language change.
            Splash()
        }
    }
}

private const val SPLASH_HOLD_MS = 1_200L

@Composable
private fun Splash() {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(SPLASH_HOLD_MS)
        visible = false
    }
    AnimatedVisibility(visible, enter = EnterTransition.None, exit = fadeOut(tween(SpaTheme.motion.medium))) { SpaSplash() }
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

/**
 * Re-read as the guest moves about, like the unread badge beside it: a visit the spa confirms or
 * declines while the app is open changes what the strip should say, and nothing else tells it.
 */
@Composable
private fun CurrentVisitRefresh(current: String) {
    val visit: CurrentVisit = koinInject()
    val sessions: AccessTokenSource = koinInject()
    val session = sessions.accessToken()
    LaunchedEffect(current, session) { visit.refresh() }
}

@Composable
private fun CurrentRoute(navigator: Navigator, current: String, notificationsEnabled: Boolean) {
    // A route may carry an argument ("booking?code=JADE20"); entries are registered under the name.
    val route = current.routeName()
    // Keeps each screen's saveable state (scroll position above all) while it is off screen, so
    // coming back from a detail page lands where the guest left the list.
    val screenState = rememberSaveableStateHolder()
    if (route in FULL_SCREEN_ROUTES) {
        RouteContent(navigator, route, screenState)
        return
    }

    val badge: NotificationBadge = koinInject()
    val unread by badge.unreadCount.collectAsStateWithLifecycle()

    // Read per route change rather than observed: a sign-in or sign-out always navigates.
    val sessions: SessionStore = koinInject()
    val account = remember(current) { sessions.load()?.user }
    val owner = account?.role == UserRoleName.OWNER

    AppScaffold(
        navItems = navDestinations(
            owner = owner,
            notificationsEnabled = notificationsEnabled,
            unread = unread,
            labels = NavLabels(
                book = stringResource(Res.string.nav_book),
                alerts = stringResource(Res.string.nav_alerts),
                offers = stringResource(Res.string.nav_offers),
                desk = stringResource(Res.string.nav_desk),
                settings = stringResource(Res.string.nav_settings),
            ),
        ),
        // A treatment's detail page and the guest's own visits belong to the booking flow, so
        // Book stays the lit destination on both.
        selectedRoute = if (route in BOOKING_FLOW_ROUTES) Routes.BOOKING else route,
        onSelect = { navigator.navigate(it) },
        bottomStrip = {
            if (route !in NO_VISIT_STRIP_ROUTES) {
                CurrentVisitStrip(
                    onOpen = { navigator.navigate(Routes.MY_BOOKINGS.withArg(Routes.ARG_BOOKING, it)) },
                )
            }
        },
        topBarActions = {
            // The only other way into the visits list is the sheet shown right after booking, so
            // without this a guest who reopens the app cannot see or cancel what they booked.
            IconButton(onClick = { navigator.navigate(Routes.MY_BOOKINGS) }) {
                SpaIconImage(
                    icon = SpaIcon.CalendarCheck,
                    size = 22.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = stringResource(Res.string.nav_visits),
                )
            }
            SpaAvatar(
                initials = initialsOf(account?.displayName.orEmpty()),
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clickable(role = Role.Button) { navigator.navigate(Routes.SETTINGS) },
            )
        },
    ) {
        RouteContent(navigator, route, screenState)
    }
}

/**
 * One screen of the back stack, with the move to it animated. The route name, not the full route,
 * is the content key: adding an argument to the route being shown is the same screen, so a
 * discount code arriving on the booking route must not replay the transition.
 */
@Composable
private fun RouteContent(navigator: Navigator, route: String, screenState: SaveableStateHolder) {
    val motion = SpaTheme.motion
    AnimatedContent(
        targetState = route to navigator.depth,
        contentKey = { (shown, _) -> shown },
        transitionSpec = { routeTransition(targetState.second >= initialState.second, motion) },
        label = "route",
    ) { (shown, _) ->
        val entry = FeaturesRegistry.entries.firstOrNull { it.route == shown }
        screenState.SaveableStateProvider(shown) { entry?.Content(navigator) }
    }
}

/** A sixth of the width: enough to read as travel, short of the screen sliding fully aside. */
private const val SLIDE_FRACTION = 6

/**
 * Going deeper arrives from the trailing edge and leaves towards the leading one, and coming back
 * reverses both, so the motion agrees with the direction the back stack moved.
 */
private fun routeTransition(forward: Boolean, motion: SpaMotion): ContentTransform {
    if (motion.reduced) return EnterTransition.None togetherWith ExitTransition.None
    val edge = if (forward) 1 else -1
    val arriving = slideInHorizontally(tween(motion.medium)) { width -> edge * width / SLIDE_FRACTION } +
        fadeIn(tween(motion.medium))
    val leaving = slideOutHorizontally(tween(motion.exit)) { width -> -edge * width / SLIDE_FRACTION } +
        fadeOut(tween(motion.exit))
    return arriving togetherWith leaving
}

/** The words on the destinations, read from resources so they follow the chosen language. */
data class NavLabels(val book: String, val alerts: String, val offers: String, val desk: String, val settings: String)

/**
 * The destinations the signed-in account sees. Desk is the spa's own console: the server refuses
 * its calls for every other role, so leaving it out only keeps it out of a guest's way.
 */
internal fun navDestinations(owner: Boolean, notificationsEnabled: Boolean, unread: Int, labels: NavLabels): List<NavConfig> = buildList {
    add(NavConfig(Routes.BOOKING, labels.book, SpaIcon.CalendarPlus))
    add(NavConfig(Routes.NOTIFICATIONS, labels.alerts, SpaIcon.Bell, badgeCount = if (notificationsEnabled) unread else 0))
    add(NavConfig(Routes.PROMOTIONS, labels.offers, SpaIcon.Tag))
    if (owner) add(NavConfig(Routes.ADMIN_REQUESTS, labels.desk, SpaIcon.Calendar))
    add(NavConfig(Routes.SETTINGS, labels.settings, SpaIcon.Gear))
}
