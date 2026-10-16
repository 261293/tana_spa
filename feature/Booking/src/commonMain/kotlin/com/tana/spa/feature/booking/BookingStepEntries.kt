package com.tana.spa.feature.booking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes
import com.tana.spa.feature.booking.presentation.BookingViewModel
import com.tana.spa.feature.booking.presentation.ui.BookingConfirmScreen
import com.tana.spa.feature.booking.presentation.ui.BookingTherapistScreen
import com.tana.spa.feature.booking.presentation.ui.BookingWhenScreen
import org.koin.compose.viewmodel.koinViewModel

/**
 * Steps two to four of the booking flow. Each resolves the same [BookingViewModel] from the one
 * store the app keeps, so the treatment chosen on the first screen is still chosen here; going
 * back is ordinary navigation, which is what lets the browser's Back button work as well.
 */
object BookingTherapistEntry : FeatureEntry {
    override val route: String = Routes.BOOKING_THERAPIST

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: BookingViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        BookingTherapistScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            onOpenNotifications = { navigator.navigate(Routes.NOTIFICATIONS) },
            onOpenBookings = { navigator.navigate(Routes.MY_BOOKINGS) },
            onBack = navigator::back,
            onContinue = { navigator.navigate(Routes.BOOKING_WHEN) },
        )
    }
}

object BookingWhenEntry : FeatureEntry {
    override val route: String = Routes.BOOKING_WHEN

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: BookingViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        BookingWhenScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            onOpenNotifications = { navigator.navigate(Routes.NOTIFICATIONS) },
            onOpenBookings = { navigator.navigate(Routes.MY_BOOKINGS) },
            onBack = navigator::back,
            onContinue = { navigator.navigate(Routes.BOOKING_CONFIRM) },
        )
    }
}

object BookingConfirmEntry : FeatureEntry {
    override val route: String = Routes.BOOKING_CONFIRM

    @Composable
    override fun Content(navigator: Navigator) {
        val viewModel: BookingViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        BookingConfirmScreen(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            onOpenNotifications = { navigator.navigate(Routes.NOTIFICATIONS) },
            onOpenBookings = { navigator.navigate(Routes.MY_BOOKINGS) },
            onBack = navigator::back,
        )
    }
}
