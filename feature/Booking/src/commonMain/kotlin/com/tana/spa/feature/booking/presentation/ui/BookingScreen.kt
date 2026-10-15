package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
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
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.core.ui.components.spaFelt
import com.tana.spa.core.ui.components.spaPocket
import com.tana.spa.feature.booking.presentation.BookingEffect
import com.tana.spa.feature.booking.presentation.BookingEvent
import com.tana.spa.feature.booking.presentation.BookingState
import com.tana.spa.feature.booking.resources.Res
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
import com.tana.spa.shared.spa.formatLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Stateless: renders [state], reports intents through [onEvent], routes one-shot [effects]. */
@Composable
fun BookingScreen(state: BookingState, effects: Flow<BookingEffect>, onEvent: (BookingEvent) -> Unit, onOpenNotifications: () -> Unit) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                BookingEffect.NavigateToNotifications -> onOpenNotifications()
            }
        }
    }

    if (SpaWindow.size.isWide) {
        WideBooking(state, onEvent)
    } else {
        CompactBooking(state, onEvent)
    }
    if (state.requestSent) RequestSentDialog(state, onEvent)
}

@Composable
private fun CompactBooking(state: BookingState, onEvent: (BookingEvent) -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var reviewTop by remember { mutableStateOf(Int.MAX_VALUE) }
    // One moss pocket on screen at a time: the strip steps aside once the review is in view.
    val stripVisible by remember {
        derivedStateOf { reviewTop > scroll.value + scroll.viewportSize - 120 }
    }

    Box(Modifier.fillMaxSize()) {
        SpaPage(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll),
            maxWidth = SpaPageWidth.Reading,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            SpaScreenHeader(
                title = stringResource(Res.string.booking_title),
                accent = stringResource(Res.string.booking_title_accent),
                subtitle = stringResource(Res.string.booking_subtitle),
            )

            Step(1, stringResource(Res.string.booking_step_treatment), state.service != null) {
                ServiceList(state.serviceId, onSelect = { onEvent(BookingEvent.ServiceSelected(it)) })
            }

            Step(2, stringResource(Res.string.booking_step_therapist), state.therapist != null) {
                TherapistPicker(state.therapistId, onSelect = { onEvent(BookingEvent.TherapistSelected(it)) }, columns = 2)
            }

            Step(3, stringResource(Res.string.booking_step_when), state.slot != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().spaFelt().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = state.day.formatLong(),
                        style = SpaTheme.textStyles.cardTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    DateRail(state.day, onSelect = { onEvent(BookingEvent.DaySelected(it)) })
                    SlotGrid(
                        day = state.day,
                        selected = state.slot,
                        onSelect = { onEvent(BookingEvent.SlotSelected(it)) },
                        columns = 3,
                    )
                }
            }

            Box(Modifier.onGloballyPositioned { reviewTop = it.positionInParent().y.toInt() }) {
                Step(4, stringResource(Res.string.booking_step_review), state.canRequest) {
                    SpaCard(padding = PaddingValues(20.dp)) {
                        BookingReview(state, onEvent)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = stripVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        ) {
            ReservationStrip(state) {
                scope.launch { scroll.animateScrollTo(reviewTop) }
            }
        }
    }
}

@Composable
private fun WideBooking(state: BookingState, onEvent: (BookingEvent) -> Unit) {
    val threeColumns = SpaWindow.size == SpaWindowSize.LARGE
    SpaPage(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        maxWidth = SpaPageWidth.Full,
    ) {
        SpaScreenHeader(
            title = stringResource(Res.string.booking_title),
            accent = stringResource(Res.string.booking_title_accent),
            subtitle = stringResource(Res.string.booking_subtitle),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Step(1, stringResource(Res.string.booking_step_treatment), state.service != null) {
                    ServiceList(state.serviceId, onSelect = { onEvent(BookingEvent.ServiceSelected(it)) })
                }
                if (!threeColumns) {
                    Step(2, stringResource(Res.string.booking_step_therapist), state.therapist != null) {
                        TherapistPicker(
                            selectedId = state.therapistId,
                            onSelect = { onEvent(BookingEvent.TherapistSelected(it)) },
                            columns = 2,
                        )
                    }
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                if (threeColumns) {
                    Step(2, stringResource(Res.string.booking_step_therapist), state.therapist != null) {
                        TherapistPicker(
                            selectedId = state.therapistId,
                            onSelect = { onEvent(BookingEvent.TherapistSelected(it)) },
                            columns = 2,
                        )
                    }
                }
                Step(3, stringResource(Res.string.booking_step_when), state.slot != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().spaFelt().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MonthCalendar(
                            selected = state.day,
                            onSelect = { onEvent(BookingEvent.DaySelected(it)) },
                        )
                        Text(
                            text = stringResource(Res.string.booking_open_times, state.day.formatLong()),
                            style = SpaTheme.textStyles.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SlotGrid(
                            day = state.day,
                            selected = state.slot,
                            onSelect = { onEvent(BookingEvent.SlotSelected(it)) },
                            columns = 3,
                        )
                    }
                }
            }

            SpaCard(
                modifier = Modifier.widthIn(max = 360.dp).weight(0.9f),
                padding = PaddingValues(24.dp),
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.booking_step_review),
                        style = SpaTheme.textStyles.screenTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(16.dp))
                    BookingReview(state, onEvent)
                }
            }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, done: Boolean, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepHeader(number, title, done)
        content()
    }
}

/** Keeps the choice in view while the guest scrolls the three steps. */
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
                text = "${state.day.formatLong()} · ${state.slot ?: stringResource(Res.string.booking_strip_pick_time)} · " +
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
