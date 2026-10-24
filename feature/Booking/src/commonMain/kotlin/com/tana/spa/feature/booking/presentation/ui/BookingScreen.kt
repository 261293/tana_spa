package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.SpaWindow
import com.tana.spa.core.ui.SpaWindowSize
import com.tana.spa.core.ui.components.SpaDisplayTitle
import com.tana.spa.core.ui.components.SpaEyebrow
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaPanel
import com.tana.spa.core.ui.components.SpaStepTrail
import com.tana.spa.core.ui.components.spaPocket
import com.tana.spa.core.ui.formatHour
import com.tana.spa.core.ui.formatRelative
import com.tana.spa.feature.booking.presentation.BookingEffect
import com.tana.spa.feature.booking.presentation.BookingEvent
import com.tana.spa.feature.booking.presentation.BookingState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.booking_change
import com.tana.spa.feature.booking.resources.booking_eyebrow
import com.tana.spa.feature.booking.resources.booking_open_times
import com.tana.spa.feature.booking.resources.booking_price
import com.tana.spa.feature.booking.resources.booking_step_review
import com.tana.spa.feature.booking.resources.booking_step_therapist
import com.tana.spa.feature.booking.resources.booking_step_treatment
import com.tana.spa.feature.booking.resources.booking_step_when
import com.tana.spa.feature.booking.resources.booking_strip_action
import com.tana.spa.feature.booking.resources.booking_strip_choose
import com.tana.spa.feature.booking.resources.booking_strip_pick_time
import com.tana.spa.feature.booking.resources.booking_subtitle
import com.tana.spa.feature.booking.resources.booking_title
import com.tana.spa.feature.booking.resources.booking_title_accent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Stateless: renders [state], reports intents through [onEvent], routes one-shot [effects]. */
@Composable
fun BookingScreen(
    state: BookingState,
    effects: Flow<BookingEffect>,
    onEvent: (BookingEvent) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBookings: () -> Unit,
    onOpenService: (String) -> Unit,
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
        if (SpaWindow.size.isWide) {
            WideBooking(state, onEvent, onOpenService)
        } else {
            CompactBooking(state, onEvent, onOpenService)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    state.sent?.let { RequestSentDialog(it, onEvent) }
}

@Composable
private fun BookingHeader(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpaEyebrow(stringResource(Res.string.booking_eyebrow))
        SpaDisplayTitle(
            lead = stringResource(Res.string.booking_title),
            accent = stringResource(Res.string.booking_title_accent),
        )
        Text(
            text = stringResource(Res.string.booking_subtitle),
            style = SpaTheme.textStyles.sectionLead,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The trail marks the first step still missing an answer. */
private fun BookingState.currentStep(): Int = when {
    service == null -> 0
    therapist == null -> 1
    slot == null -> 2
    else -> 3
}

@Composable
private fun stepLabels(): List<String> = listOf(
    stringResource(Res.string.booking_step_treatment),
    stringResource(Res.string.booking_step_therapist),
    stringResource(Res.string.booking_step_when),
    stringResource(Res.string.booking_step_review),
)

/** Choosing a treatment also opens its detail; the flow keeps the choice, so Back there resumes it. */
@Composable
private fun TreatmentPanel(state: BookingState, onEvent: (BookingEvent) -> Unit, onOpenService: (String) -> Unit) {
    SpaPanel(title = stringResource(Res.string.booking_step_treatment)) {
        ServiceList(
            state = state,
            onSelect = {
                onEvent(BookingEvent.ServiceSelected(it))
                onOpenService(it)
            },
            onRetry = { onEvent(BookingEvent.RetryCatalog) },
        )
    }
}

@Composable
private fun TherapistPanel(state: BookingState, onEvent: (BookingEvent) -> Unit) {
    SpaPanel(title = stringResource(Res.string.booking_step_therapist)) {
        TherapistPicker(state, onSelect = { onEvent(BookingEvent.TherapistSelected(it)) })
    }
}

@Composable
private fun WhenPanel(state: BookingState, onEvent: (BookingEvent) -> Unit, calendar: Boolean) {
    SpaPanel(title = stringResource(Res.string.booking_step_when)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (calendar) {
                MonthCalendar(state.day, state.today, onSelect = { onEvent(BookingEvent.DaySelected(it)) })
            } else {
                DateRail(state.day, state.today, onSelect = { onEvent(BookingEvent.DaySelected(it)) })
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
private fun CompactBooking(state: BookingState, onEvent: (BookingEvent) -> Unit, onOpenService: (String) -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var reviewTop by remember { mutableStateOf(Int.MAX_VALUE) }
    // One dark panel on screen at a time: the strip steps aside once the review is in view.
    val stripVisible by remember {
        derivedStateOf { reviewTop > scroll.value + scroll.viewportSize - 120 }
    }

    Box(Modifier.fillMaxSize()) {
        SpaPage(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll),
            maxWidth = SpaPageWidth.Reading,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BookingHeader()
            SpaStepTrail(stepLabels(), state.currentStep())
            TreatmentPanel(state, onEvent, onOpenService)
            TherapistPanel(state, onEvent)
            WhenPanel(state, onEvent, calendar = false)

            Box(Modifier.onGloballyPositioned { reviewTop = it.positionInParent().y.toInt() }) {
                SpaPanel(title = stringResource(Res.string.booking_step_review)) {
                    BookingReview(state, onEvent)
                }
            }
        }

        AnimatedVisibility(
            visible = stripVisible,
            enter = fadeIn(tween(SpaTheme.motion.medium)),
            exit = fadeOut(tween(SpaTheme.motion.exit)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        ) {
            ReservationStrip(state) {
                scope.launch { scroll.animateScrollTo(reviewTop) }
            }
        }
    }
}

@Composable
private fun WideBooking(state: BookingState, onEvent: (BookingEvent) -> Unit, onOpenService: (String) -> Unit) {
    val threeColumns = SpaWindow.size == SpaWindowSize.LARGE
    SpaPage(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        maxWidth = SpaPageWidth.Full,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        BookingHeader()
        SpaStepTrail(stepLabels(), state.currentStep())
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                TreatmentPanel(state, onEvent, onOpenService)
                if (!threeColumns) TherapistPanel(state, onEvent)
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (threeColumns) TherapistPanel(state, onEvent)
                WhenPanel(state, onEvent, calendar = true)
            }

            SpaPanel(
                modifier = Modifier.widthIn(max = 360.dp).weight(0.9f),
                title = stringResource(Res.string.booking_step_review),
            ) {
                BookingReview(state, onEvent)
            }
        }
    }
}

/** Keeps the choice in view while the guest scrolls the steps. */
@Composable
private fun ReservationStrip(state: BookingState, onReview: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().spaPocket().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
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
        StripButton(stringResource(Res.string.booking_strip_action), onReview)
    }
}

@Composable
private fun StripButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
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
