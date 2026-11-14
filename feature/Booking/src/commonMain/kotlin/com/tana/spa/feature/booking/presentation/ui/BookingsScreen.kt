package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaPhotos
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaDateBlock
import com.tana.spa.core.ui.components.SpaEmptyBlock
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaImageTile
import com.tana.spa.core.ui.components.SpaListRow
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaPanel
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaRowGroup
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.core.ui.components.SpaSkeleton
import com.tana.spa.core.ui.components.SpaStepper
import com.tana.spa.core.ui.components.SpaTextButton
import com.tana.spa.core.ui.formatDayAndTime
import com.tana.spa.core.ui.formatPrice
import com.tana.spa.core.ui.monthShort
import com.tana.spa.core.ui.rememberSpaScrollState
import com.tana.spa.core.ui.revealOnScroll
import com.tana.spa.core.ui.weekdayShort
import com.tana.spa.feature.booking.domain.GuestBooking
import com.tana.spa.feature.booking.domain.visitStep
import com.tana.spa.feature.booking.presentation.BookingsEffect
import com.tana.spa.feature.booking.presentation.BookingsEvent
import com.tana.spa.feature.booking.presentation.BookingsState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.bookings_book_now
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
import com.tana.spa.feature.booking.resources.bookings_past
import com.tana.spa.feature.booking.resources.bookings_retry
import com.tana.spa.feature.booking.resources.bookings_status_completed
import com.tana.spa.feature.booking.resources.bookings_status_confirmed
import com.tana.spa.feature.booking.resources.bookings_status_pending
import com.tana.spa.feature.booking.resources.bookings_subtitle
import com.tana.spa.feature.booking.resources.bookings_suggestions
import com.tana.spa.feature.booking.resources.bookings_title
import com.tana.spa.feature.booking.resources.bookings_title_accent
import com.tana.spa.feature.booking.resources.bookings_too_late
import com.tana.spa.feature.booking.resources.bookings_upcoming
import com.tana.spa.feature.booking.resources.home_treatment_meta
import com.tana.spa.feature.booking.resources.visit_in_progress
import com.tana.spa.feature.booking.resources.visit_progress_description
import com.tana.spa.shared.booking.BookingStatusDto
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.spa.SpaDate
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.rememberResourceEnvironment
import org.jetbrains.compose.resources.stringResource

/** Stateless: renders [state], reports intents through [onEvent], routes one-shot [effects]. */
@Composable
fun BookingsScreen(
    state: BookingsState,
    effects: Flow<BookingsEffect>,
    onEvent: (BookingsEvent) -> Unit,
    onBook: () -> Unit,
    onBookService: (String) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val resources = rememberResourceEnvironment()
    LaunchedEffect(effects) {
        effects.collect { effect ->
            val message = when (effect) {
                BookingsEffect.ShowCancelled -> getString(resources, Res.string.bookings_cancelled_toast)
                is BookingsEffect.ShowRefusal -> getString(resources, refusalText(effect.code))
            }
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    Box(Modifier.fillMaxSize()) {
        SpaPage(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberSpaScrollState()),
            maxWidth = SpaPageWidth.Reading,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpaScreenHeader(
                title = stringResource(Res.string.bookings_title),
                accent = stringResource(Res.string.bookings_title_accent),
                subtitle = stringResource(Res.string.bookings_subtitle),
                modifier = Modifier.revealOnScroll(),
            )
            when {
                state.loading && state.bookings.isEmpty() -> repeat(SKELETONS) { SpaSkeleton(ROW_HEIGHT) }

                state.failed && state.bookings.isEmpty() -> SpaEmptyBlock(stringResource(Res.string.bookings_failed)) {
                    SpaTextButton(stringResource(Res.string.bookings_retry), onClick = { onEvent(BookingsEvent.Refresh) })
                }

                else -> {
                    Group(stringResource(Res.string.bookings_upcoming)) {
                        if (state.upcoming.isEmpty()) {
                            SpaEmptyBlock(stringResource(Res.string.bookings_empty)) {
                                SpaPrimaryButton(
                                    stringResource(Res.string.bookings_book_now),
                                    onClick = onBook,
                                    modifier = Modifier.widthIn(max = ActionWidth),
                                )
                            }
                        }
                        state.upcoming.forEachIndexed { index, booking -> Row(booking, state, onEvent, index) }
                        if (state.upcoming.isEmpty() && state.suggestions.isNotEmpty()) {
                            SpaRowGroup(
                                title = stringResource(Res.string.bookings_suggestions),
                                rows = state.suggestions.map { service ->
                                    {
                                        SpaListRow(
                                            title = service.name,
                                            supporting = stringResource(
                                                Res.string.home_treatment_meta,
                                                service.minutes,
                                                formatPrice(service.priceVnd),
                                            ),
                                            leading = {
                                                SpaImageTile(
                                                    Modifier.size(40.dp),
                                                    shape = MaterialTheme.shapes.small,
                                                    photo = SpaPhotos.treatment(service.id),
                                                )
                                            },
                                            onClick = { onBookService(service.id) },
                                        )
                                    }
                                },
                            )
                        }
                    }
                    if (state.past.isNotEmpty()) {
                        Group(stringResource(Res.string.bookings_past)) {
                            state.past.forEachIndexed { index, booking -> Row(booking, state, onEvent, index) }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    state.cancelling?.let { CancelDialog(it, onEvent) }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = SpaTheme.textStyles.body,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Row(booking: GuestBooking, state: BookingsState, onEvent: (BookingsEvent) -> Unit, index: Int) {
    BookingRow(
        booking = booking,
        nowEpochMillis = state.nowEpochMillis,
        enabled = !state.working,
        opened = booking.id == state.openedId,
        onEvent = onEvent,
        modifier = Modifier.revealOnScroll(order = index),
    )
}

@Composable
private fun BookingRow(
    booking: GuestBooking,
    nowEpochMillis: Long,
    enabled: Boolean,
    opened: Boolean,
    onEvent: (BookingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = SpaDate.fromEpochDay(booking.startDayEpochDay)
    SpaPanel(modifier = modifier.fillMaxWidth().clickable { onEvent(BookingsEvent.RowToggled(booking.id)) }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SpaDateBlock(date.weekdayShort(), date.day.toString(), date.monthShort())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = booking.serviceName,
                            style = SpaTheme.textStyles.cardTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        BookingStatusPill(booking.status)
                    }
                    Text(
                        text = stringResource(
                            Res.string.bookings_meta,
                            formatDayAndTime(booking.startDayEpochDay, booking.startHour),
                            formatPrice(booking.total),
                        ),
                        style = SpaTheme.textStyles.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (opened) VisitProgress(booking, nowEpochMillis)

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
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            when {
                booking.cancellable(nowEpochMillis) -> {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { onEvent(BookingsEvent.CancelStarted(booking.id)) },
                            enabled = enabled,
                        ) {
                            Text(
                                text = stringResource(Res.string.bookings_cancel),
                                style = SpaTheme.textStyles.caption,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // Still ahead, but inside the window the spa keeps the hour for: say so plainly.
                booking.cancelTooLate(nowEpochMillis) -> Text(
                    text = stringResource(Res.string.bookings_too_late),
                    style = SpaTheme.textStyles.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun CancelDialog(booking: GuestBooking, onEvent: (BookingsEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(BookingsEvent.CancelDismissed) },
        containerColor = MaterialTheme.colorScheme.surface,
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

private fun declineText(reason: DeclineReasonDto) = when (reason) {
    DeclineReasonDto.OVERLAP -> Res.string.bookings_decline_overlap
    DeclineReasonDto.THERAPIST_UNAVAILABLE -> Res.string.bookings_decline_therapist
    DeclineReasonDto.NO_ROOM -> Res.string.bookings_decline_room
}

private const val SKELETONS = 3
private val ROW_HEIGHT = 120.dp
private val ActionWidth = 320.dp
