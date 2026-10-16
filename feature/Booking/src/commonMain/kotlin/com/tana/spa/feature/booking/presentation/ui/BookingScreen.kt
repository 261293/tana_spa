package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.SpaWindow
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.core.ui.components.spaFelt
import com.tana.spa.core.ui.components.spaPocket
import com.tana.spa.core.ui.formatHour
import com.tana.spa.core.ui.formatRelative
import com.tana.spa.feature.booking.presentation.BookingEffect
import com.tana.spa.feature.booking.presentation.BookingEvent
import com.tana.spa.feature.booking.presentation.BookingState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.booking_back
import com.tana.spa.feature.booking.resources.booking_continue
import com.tana.spa.feature.booking.resources.booking_open_times
import com.tana.spa.feature.booking.resources.booking_price
import com.tana.spa.feature.booking.resources.booking_step_review
import com.tana.spa.feature.booking.resources.booking_step_therapist
import com.tana.spa.feature.booking.resources.booking_step_treatment
import com.tana.spa.feature.booking.resources.booking_step_when
import com.tana.spa.feature.booking.resources.booking_strip_choose
import com.tana.spa.feature.booking.resources.booking_strip_pick_time
import com.tana.spa.feature.booking.resources.booking_subtitle
import com.tana.spa.feature.booking.resources.booking_title
import com.tana.spa.feature.booking.resources.booking_title_accent
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * The booking flow is four screens, one to a step. They share a single view model, so a choice
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
        title = stringResource(Res.string.booking_step_treatment),
        done = state.service != null,
        effects = effects,
        onOpenNotifications = onOpenNotifications,
        onOpenBookings = onOpenBookings,
        header = {
            SpaScreenHeader(
                title = stringResource(Res.string.booking_title),
                accent = stringResource(Res.string.booking_title_accent),
                subtitle = stringResource(Res.string.booking_subtitle),
            )
        },
        footer = { StepFooter(state, canContinue = state.service != null, onBack = null, onContinue = onContinue) },
    ) {
        ServiceList(
            state = state,
            onSelect = { onEvent(BookingEvent.ServiceSelected(it)) },
            onDetails = onOpenService,
            onRetry = { onEvent(BookingEvent.RetryCatalog) },
            columns = catalogueColumns(SpaWindow.size),
        )
    }
}

@Composable
fun BookingTherapistScreen(
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
        title = stringResource(Res.string.booking_step_therapist),
        done = state.therapist != null,
        effects = effects,
        onOpenNotifications = onOpenNotifications,
        onOpenBookings = onOpenBookings,
        footer = { StepFooter(state, canContinue = state.therapist != null, onBack = onBack, onContinue = onContinue) },
    ) {
        TherapistPicker(
            state = state,
            onSelect = { onEvent(BookingEvent.TherapistSelected(it)) },
            columns = catalogueColumns(SpaWindow.size) + 1,
        )
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
        step = 3,
        title = stringResource(Res.string.booking_step_when),
        done = state.slot != null,
        effects = effects,
        onOpenNotifications = onOpenNotifications,
        onOpenBookings = onOpenBookings,
        footer = { StepFooter(state, canContinue = state.slot != null, onBack = onBack, onContinue = onContinue) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().spaFelt().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // A phone gets the fortnight rail; from a tablet up there is room for the whole month.
            if (SpaWindow.size.isCompact) {
                Text(
                    text = state.day.formatRelative(state.today),
                    style = SpaTheme.textStyles.cardTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                DateRail(state.day, state.today, onSelect = { onEvent(BookingEvent.DaySelected(it)) })
            } else {
                MonthCalendar(
                    selected = state.day,
                    today = state.today,
                    onSelect = { onEvent(BookingEvent.DaySelected(it)) },
                )
            }
            Text(
                text = stringResource(Res.string.booking_open_times, state.day.formatRelative(state.today)),
                style = SpaTheme.textStyles.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SlotGrid(state = state, onSelect = { onEvent(BookingEvent.SlotSelected(it)) }, columns = 3)
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
        step = 4,
        title = stringResource(Res.string.booking_step_review),
        done = state.canRequest,
        effects = effects,
        onOpenNotifications = onOpenNotifications,
        onOpenBookings = onOpenBookings,
        // The request itself is the card's own button, so the strip only offers the way back.
        footer = { StepFooter(state, canContinue = false, onBack = onBack, onContinue = null) },
    ) {
        SpaCard(padding = PaddingValues(20.dp)) {
            BookingReview(state, onEvent)
        }
    }
    state.sent?.let { RequestSentDialog(it, onEvent) }
}

/**
 * One step's page: the heading, the step's own content, and the moss pocket holding the choice so
 * far. Refusals are shown here rather than per step, because the server can refuse a slot the
 * guest chose two screens ago.
 */
@Composable
private fun BookingStepPage(
    step: Int,
    title: String,
    done: Boolean,
    effects: Flow<BookingEffect>,
    onOpenNotifications: () -> Unit,
    onOpenBookings: () -> Unit,
    footer: @Composable () -> Unit,
    header: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                BookingEffect.NavigateToNotifications -> onOpenNotifications()

                BookingEffect.NavigateToBookings -> onOpenBookings()

                is BookingEffect.ShowRefusal -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(getString(refusalText(effect.code)))
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        SpaPage(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            maxWidth = if (SpaWindow.size.isCompact) SpaPageWidth.Reading else SpaPageWidth.Full,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            header()
            StepHeader(step, title, done)
            content()
            // Nothing of the step hides under the pocket at the end of the scroll.
            Spacer(Modifier.height(FOOTER_CLEARANCE))
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)) { footer() }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Keeps the choice in view from step to step, and carries the way on and the way back. */
@Composable
private fun StepFooter(state: BookingState, canContinue: Boolean, onBack: (() -> Unit)?, onContinue: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().spaPocket().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let {
            TextButton(
                onClick = it,
                colors = ButtonDefaults.textButtonColors(contentColor = SpaTheme.extraColors.onPocketMuted),
            ) {
                Text(stringResource(Res.string.booking_back), style = SpaTheme.textStyles.body)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                text = state.service?.name ?: stringResource(Res.string.booking_strip_choose),
                style = SpaTheme.textStyles.body,
                color = SpaTheme.extraColors.onPocket,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                "${state.day.formatRelative(state.today)} · ${state.slot?.let {
                    formatHour(it.startHour)
                } ?: stringResource(Res.string.booking_strip_pick_time)} · " +
                    stringResource(Res.string.booking_price, state.totalUsd),
                style = SpaTheme.textStyles.caption,
                color = SpaTheme.extraColors.onPocketMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        onContinue?.let { StripButton(stringResource(Res.string.booking_continue), canContinue, it) }
    }
}

@Composable
private fun StripButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = SpaTheme.extraColors.pocketAction,
            contentColor = SpaTheme.extraColors.onPocketAction,
        ),
        modifier = Modifier.height(40.dp),
    ) {
        Text(text, style = SpaTheme.textStyles.body)
    }
}

private val FOOTER_CLEARANCE = 72.dp
