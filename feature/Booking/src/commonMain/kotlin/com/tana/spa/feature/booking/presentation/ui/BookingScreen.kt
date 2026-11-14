package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.tana.spa.core.ui.SpaWindow
import com.tana.spa.core.ui.components.SpaBackLink
import com.tana.spa.core.ui.components.SpaDateBlock
import com.tana.spa.core.ui.components.SpaDisplayTitle
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaPanel
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaStepTrail
import com.tana.spa.core.ui.components.SpaTag
import com.tana.spa.core.ui.formatHour
import com.tana.spa.core.ui.formatPrice
import com.tana.spa.core.ui.formatRelative
import com.tana.spa.core.ui.monthShort
import com.tana.spa.core.ui.rememberSpaScrollState
import com.tana.spa.core.ui.revealOnScroll
import com.tana.spa.core.ui.spaPointer
import com.tana.spa.core.ui.weekdayShort
import com.tana.spa.feature.booking.presentation.BookingEffect
import com.tana.spa.feature.booking.presentation.BookingEvent
import com.tana.spa.feature.booking.presentation.BookingState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.booking_back
import com.tana.spa.feature.booking.resources.booking_card_minutes
import com.tana.spa.feature.booking.resources.booking_card_need_service
import com.tana.spa.feature.booking.resources.booking_card_need_time
import com.tana.spa.feature.booking.resources.booking_card_no_time
import com.tana.spa.feature.booking.resources.booking_card_title
import com.tana.spa.feature.booking.resources.booking_continue
import com.tana.spa.feature.booking.resources.booking_open_times
import com.tana.spa.feature.booking.resources.booking_service_details
import com.tana.spa.feature.booking.resources.booking_step_review
import com.tana.spa.feature.booking.resources.booking_step_treatment
import com.tana.spa.feature.booking.resources.booking_step_when
import com.tana.spa.feature.booking.resources.booking_strip_choose
import com.tana.spa.feature.booking.resources.booking_subtitle
import com.tana.spa.feature.booking.resources.booking_title
import com.tana.spa.feature.booking.resources.booking_title_accent
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.rememberResourceEnvironment
import org.jetbrains.compose.resources.stringResource

/**
 * The booking flow is three screens, one to a step. They share a single view model, so a choice
 * made on one is still there whichever way the guest moves between them, and the step the guest
 * is on is the route — which is also what the browser's address bar and Back button follow.
 */
@Composable
fun BookingTreatmentScreen(
    state: BookingState,
    effects: Flow<BookingEffect>,
    onEvent: (BookingEvent) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBookings: () -> Unit,
    onOpenService: (String) -> Unit,
    onContinue: () -> Unit,
) {
    BookingStepPage(
        step = 1,
        effects = effects,
        onOpenNotifications = onOpenNotifications,
        onOpenBookings = onOpenBookings,
        header = { BookingHeader(Modifier.revealOnScroll()) },
        footer = {
            AppointmentCard(
                state,
                canContinue = state.service != null,
                hint = stringResource(Res.string.booking_card_need_service),
                onContinue = onContinue,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CatalogueFilters(state, onEvent)
            ServiceList(
                state = state,
                onSelect = { onEvent(BookingEvent.ServiceSelected(it)) },
                onRetry = { onEvent(BookingEvent.RetryCatalog) },
                onClearFilters = { onEvent(BookingEvent.FiltersCleared) },
                columns = catalogueColumns(SpaWindow.size),
            )
            // Picking a row only picks it now, so the detail page needs a target of its own.
            state.service?.let { service ->
                SpaSecondaryButton(
                    text = stringResource(Res.string.booking_service_details),
                    onClick = { onOpenService(service.id) },
                )
            }
        }
    }
}

@Composable
fun BookingWhenScreen(
    state: BookingState,
    effects: Flow<BookingEffect>,
    onEvent: (BookingEvent) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBookings: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    BookingStepPage(
        step = 2,
        effects = effects,
        onOpenNotifications = onOpenNotifications,
        onOpenBookings = onOpenBookings,
        header = { SpaBackLink(stringResource(Res.string.booking_back), onClick = onBack) },
        footer = {
            AppointmentCard(
                state,
                canContinue = state.slot != null,
                hint = stringResource(Res.string.booking_card_need_time),
                onContinue = onContinue,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // A phone gets the fortnight rail; from a tablet up there is room for the whole month.
            if (SpaWindow.size.isCompact) {
                DateRail(state.day, state.today, onSelect = { onEvent(BookingEvent.DaySelected(it)) })
            } else {
                MonthCalendar(state.day, state.today, onSelect = { onEvent(BookingEvent.DaySelected(it)) })
            }
            Text(
                text = stringResource(Res.string.booking_open_times, state.day.formatRelative(state.today)),
                style = SpaTheme.textStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SlotGrid(state = state, onSelect = { onEvent(BookingEvent.SlotSelected(it)) })
        }
    }
}

@Composable
fun BookingConfirmScreen(
    state: BookingState,
    effects: Flow<BookingEffect>,
    onEvent: (BookingEvent) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBookings: () -> Unit,
    onBack: () -> Unit,
) {
    BookingStepPage(
        step = 3,
        effects = effects,
        onOpenNotifications = onOpenNotifications,
        onOpenBookings = onOpenBookings,
        // The review is the appointment in full and carries the request button, so no card repeats it.
        header = { SpaBackLink(stringResource(Res.string.booking_back), onClick = onBack) },
    ) {
        BookingReview(state, onEvent)
    }
    state.sent?.let { RequestSentDialog(it, onEvent) }
}

/**
 * One step's page: the trail, the step's own panel, and the appointment card holding the choice so far.
 * Refusals are shown here rather than per step, because the server can refuse a slot the guest
 * chose two screens ago.
 */
@Composable
private fun BookingStepPage(
    step: Int,
    effects: Flow<BookingEffect>,
    onOpenNotifications: () -> Unit,
    onOpenBookings: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    header: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val resources = rememberResourceEnvironment()
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                BookingEffect.NavigateToNotifications -> onOpenNotifications()

                BookingEffect.NavigateToBookings -> onOpenBookings()

                is BookingEffect.ShowRefusal -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(getString(resources, refusalText(effect.code)))
                }
            }
        }
    }

    val labels = stepLabels()
    val pageWidth = if (SpaWindow.size.isCompact) SpaPageWidth.Reading else SpaPageWidth.Full
    // The card is docked below the scroll rather than over it, so it never covers a slot or a row.
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            SpaPage(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberSpaScrollState()),
                maxWidth = pageWidth,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                header()
                SpaStepTrail(labels, current = step - 1, modifier = Modifier.revealOnScroll(order = 1))
                SpaPanel(title = labels[step - 1], modifier = Modifier.revealOnScroll(order = 2), content = content)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
        footer?.let { SpaPage(maxWidth = pageWidth, top = 8.dp, bottom = 8.dp) { it() } }
    }
}

@Composable
private fun BookingHeader(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpaDisplayTitle(
            lead = stringResource(Res.string.booking_title),
            accent = stringResource(Res.string.booking_title_accent),
        )
        // A phone spends its first screen on the menu; the subtitle only explains it.
        if (!SpaWindow.size.isCompact) {
            Text(
                text = stringResource(Res.string.booking_subtitle),
                style = SpaTheme.textStyles.sectionLead,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun appointmentLine(state: BookingState): String {
    val service = state.service ?: return stringResource(Res.string.booking_strip_choose)
    val time = state.slot?.let { formatHour(it.startHour) } ?: stringResource(Res.string.booking_card_no_time)
    return listOf(time, stringResource(Res.string.booking_card_minutes, service.minutes), formatPrice(state.totalVnd))
        .joinToString(APPOINTMENT_SEPARATOR)
}

private const val APPOINTMENT_SEPARATOR = " · "

@Composable
private fun stepLabels(): List<String> = listOf(
    stringResource(Res.string.booking_step_treatment),
    stringResource(Res.string.booking_step_when),
    stringResource(Res.string.booking_step_review),
)

/**
 * The appointment as it stands, shaped like the card it becomes under My bookings: the day, the
 * treatment, then time, length and price, with the way on beside it. When that way is closed,
 * [hint] says what is missing. The way back is the link at the top of the page, as on every
 * other screen.
 */
@Composable
private fun AppointmentCard(state: BookingState, canContinue: Boolean, hint: String, onContinue: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppointmentSummary(state, Modifier.weight(1f))
                Button(onClick = onContinue, enabled = canContinue, shape = CircleShape, modifier = Modifier.spaPointer(canContinue)) {
                    Text(stringResource(Res.string.booking_continue), style = SpaTheme.textStyles.buttonLabel)
                }
            }
            if (!canContinue) {
                Text(
                    text = hint,
                    style = SpaTheme.textStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AppointmentSummary(state: BookingState, modifier: Modifier = Modifier) {
    val day = state.day
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SpaDateBlock(day.weekdayShort(), day.day.toString(), day.monthShort())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = state.service?.name ?: stringResource(Res.string.booking_card_title),
                style = SpaTheme.textStyles.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = appointmentLine(state),
                style = SpaTheme.textStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
