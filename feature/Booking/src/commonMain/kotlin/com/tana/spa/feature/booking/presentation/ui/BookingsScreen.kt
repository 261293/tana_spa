package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaSkeleton
import com.tana.spa.core.ui.components.SpaStatusPill
import com.tana.spa.core.ui.components.SpaStepper
import com.tana.spa.core.ui.formatDayAndTime
import com.tana.spa.feature.booking.domain.GuestBooking
import com.tana.spa.feature.booking.domain.visitStep
import com.tana.spa.feature.booking.presentation.BookingsEffect
import com.tana.spa.feature.booking.presentation.BookingsEvent
import com.tana.spa.feature.booking.presentation.BookingsState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.booking_price
import com.tana.spa.feature.booking.resources.bookings_cancel
import com.tana.spa.feature.booking.resources.bookings_cancel_body
import com.tana.spa.feature.booking.resources.bookings_cancel_keep
import com.tana.spa.feature.booking.resources.bookings_cancel_title
import com.tana.spa.feature.booking.resources.bookings_cancelled_toast
import com.tana.spa.feature.booking.resources.bookings_decline_overlap
import com.tana.spa.feature.booking.resources.bookings_decline_room
import com.tana.spa.feature.booking.resources.bookings_decline_therapist
import com.tana.spa.feature.booking.resources.bookings_empty
import com.tana.spa.feature.booking.resources.bookings_failed
import com.tana.spa.feature.booking.resources.bookings_instead
import com.tana.spa.feature.booking.resources.bookings_meta
import com.tana.spa.feature.booking.resources.bookings_retry
import com.tana.spa.feature.booking.resources.bookings_status_cancelled
import com.tana.spa.feature.booking.resources.bookings_status_completed
import com.tana.spa.feature.booking.resources.bookings_status_confirmed
import com.tana.spa.feature.booking.resources.bookings_status_declined
import com.tana.spa.feature.booking.resources.bookings_status_no_show
import com.tana.spa.feature.booking.resources.bookings_status_pending
import com.tana.spa.feature.booking.resources.bookings_subtitle
import com.tana.spa.feature.booking.resources.bookings_title
import com.tana.spa.feature.booking.resources.bookings_title_accent
import com.tana.spa.feature.booking.resources.bookings_too_late
import com.tana.spa.feature.booking.resources.visit_in_progress
import com.tana.spa.feature.booking.resources.visit_progress_description
import com.tana.spa.shared.booking.BookingStatusDto
import com.tana.spa.shared.booking.DeclineReasonDto
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Stateless: renders [state], reports intents through [onEvent], routes one-shot [effects]. */
@Composable
fun BookingsScreen(state: BookingsState, effects: Flow<BookingsEffect>, onEvent: (BookingsEvent) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            val message = when (effect) {
                BookingsEffect.ShowCancelled -> getString(Res.string.bookings_cancelled_toast)
                is BookingsEffect.ShowRefusal -> getString(refusalText(effect.code))
            }
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    Box(Modifier.fillMaxSize()) {
        SpaPage(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            maxWidth = SpaPageWidth.Reading,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpaScreenHeader(
                title = stringResource(Res.string.bookings_title),
                accent = stringResource(Res.string.bookings_title_accent),
                subtitle = stringResource(Res.string.bookings_subtitle),
            )
            when {
                state.loading && state.bookings.isEmpty() -> repeat(SKELETONS) { SpaSkeleton(ROW_HEIGHT) }

                state.failed && state.bookings.isEmpty() -> Message(
                    text = stringResource(Res.string.bookings_failed),
                    action = stringResource(Res.string.bookings_retry),
                    onAction = { onEvent(BookingsEvent.Refresh) },
                )

                state.bookings.isEmpty() -> Message(text = stringResource(Res.string.bookings_empty))

                else -> state.bookings.forEach { booking ->
                    BookingRow(
                        booking = booking,
                        nowEpochMillis = state.nowEpochMillis,
                        enabled = !state.working,
                        opened = booking.id == state.openedId,
                        onEvent = onEvent,
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    state.cancelling?.let { CancelDialog(it, onEvent) }
}

/**
 * [opened] is the visit a notification sent the guest here for, or the one they tapped; its row is
 * picked out and shows how far along the visit is. The others stay a compact summary, because a
 * list of visits is read by scanning it.
 */
@Composable
private fun BookingRow(booking: GuestBooking, nowEpochMillis: Long, enabled: Boolean, opened: Boolean, onEvent: (BookingsEvent) -> Unit) {
    SpaCard(
        Modifier.fillMaxWidth(),
        padding = PaddingValues(16.dp),
        selected = opened,
        onClick = { onEvent(BookingsEvent.RowToggled(booking.id)) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = booking.serviceName,
                    style = SpaTheme.textStyles.cardTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(booking.status)
            }

            if (opened) VisitProgress(booking, nowEpochMillis)
            Text(
                text = stringResource(
                    Res.string.bookings_meta,
                    formatDayAndTime(booking.startDayEpochDay, booking.startHour),
                    booking.therapistName,
                    stringResource(Res.string.booking_price, booking.total),
                ),
                style = SpaTheme.textStyles.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            booking.declineReason?.let { reason ->
                Text(
                    text = stringResource(declineText(reason)),
                    style = SpaTheme.textStyles.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            booking.alternativeOffer?.let { offer ->
                Text(
                    text = stringResource(Res.string.bookings_instead, offer),
                    style = SpaTheme.textStyles.body,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            when {
                booking.cancellable(nowEpochMillis) -> SpaSecondaryButton(
                    text = stringResource(Res.string.bookings_cancel),
                    enabled = enabled,
                    onClick = { onEvent(BookingsEvent.CancelStarted(booking.id)) },
                    contentColor = MaterialTheme.colorScheme.error,
                )

                // Still ahead, but inside the window the spa keeps the hour for: say so plainly.
                booking.cancelTooLate(nowEpochMillis) -> Text(
                    text = stringResource(Res.string.bookings_too_late),
                    style = SpaTheme.textStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The track a visit that goes to plan runs along. A visit that ended off it draws nothing: the
 * status pill and the spa's reason, both already on the card, are the whole story in that case.
 */
@Composable
private fun VisitProgress(booking: GuestBooking, nowEpochMillis: Long) {
    val reached = visitStep(booking.status) ?: return
    val labels = listOf(
        stringResource(Res.string.bookings_status_pending),
        stringResource(Res.string.bookings_status_confirmed),
        stringResource(Res.string.bookings_status_completed),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpaStepper(
            steps = labels,
            reached = reached.ordinal,
            modifier = Modifier.fillMaxWidth(),
            description = stringResource(
                Res.string.visit_progress_description,
                reached.ordinal + 1,
                labels.size,
                labels[reached.ordinal],
            ),
        )
        if (booking.inProgress(nowEpochMillis)) {
            Text(
                text = stringResource(Res.string.visit_in_progress),
                style = SpaTheme.textStyles.caption,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StatusPill(status: BookingStatusDto) = when (status) {
    BookingStatusDto.PENDING -> SpaStatusPill(
        text = stringResource(Res.string.bookings_status_pending),
        container = SpaTheme.extraColors.warningContainer,
        content = SpaTheme.extraColors.warning,
        icon = SpaIcon.Clock,
    )

    BookingStatusDto.CONFIRMED -> SpaStatusPill(
        text = stringResource(Res.string.bookings_status_confirmed),
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.primary,
        icon = SpaIcon.Check,
    )

    BookingStatusDto.COMPLETED -> SpaStatusPill(
        text = stringResource(Res.string.bookings_status_completed),
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurface,
        icon = SpaIcon.CheckCircle,
    )

    BookingStatusDto.DECLINED -> SpaStatusPill(
        text = stringResource(Res.string.bookings_status_declined),
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        icon = SpaIcon.Close,
    )

    BookingStatusDto.CANCELLED -> SpaStatusPill(
        text = stringResource(Res.string.bookings_status_cancelled),
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        icon = SpaIcon.Close,
    )

    BookingStatusDto.NO_SHOW -> SpaStatusPill(
        text = stringResource(Res.string.bookings_status_no_show),
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        icon = SpaIcon.Prohibit,
    )
}

@Composable
private fun CancelDialog(booking: GuestBooking, onEvent: (BookingsEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(BookingsEvent.CancelDismissed) },
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = stringResource(Res.string.bookings_cancel_title, booking.serviceName),
                style = SpaTheme.textStyles.cardTitle,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.bookings_cancel_body, formatDayAndTime(booking.startDayEpochDay, booking.startHour)),
                style = SpaTheme.textStyles.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(BookingsEvent.CancelConfirmed) }) {
                Text(
                    text = stringResource(Res.string.bookings_cancel),
                    style = SpaTheme.textStyles.body,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(BookingsEvent.CancelDismissed) }) {
                Text(
                    text = stringResource(Res.string.bookings_cancel_keep),
                    style = SpaTheme.textStyles.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = text,
            style = SpaTheme.textStyles.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) SpaSecondaryButton(text = action, onClick = onAction)
    }
}

private fun declineText(reason: DeclineReasonDto) = when (reason) {
    DeclineReasonDto.OVERLAP -> Res.string.bookings_decline_overlap
    DeclineReasonDto.THERAPIST_UNAVAILABLE -> Res.string.bookings_decline_therapist
    DeclineReasonDto.NO_ROOM -> Res.string.bookings_decline_room
}

private const val SKELETONS = 3
private val ROW_HEIGHT = 120.dp
