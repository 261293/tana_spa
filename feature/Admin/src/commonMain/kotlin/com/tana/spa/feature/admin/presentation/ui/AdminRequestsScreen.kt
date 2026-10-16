package com.tana.spa.feature.admin.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaPocketButton
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaStatusPill
import com.tana.spa.core.ui.components.spaPocket
import com.tana.spa.core.ui.formatDayAndTime
import com.tana.spa.core.ui.formatFull
import com.tana.spa.core.ui.formatHour
import com.tana.spa.feature.admin.domain.DeskRequest
import com.tana.spa.feature.admin.domain.DeskStatus
import com.tana.spa.feature.admin.presentation.AdminRequestsEffect
import com.tana.spa.feature.admin.presentation.AdminRequestsEvent
import com.tana.spa.feature.admin.presentation.AdminRequestsState
import com.tana.spa.feature.admin.presentation.AlternativeOffer
import com.tana.spa.feature.admin.resources.Res
import com.tana.spa.feature.admin.resources.admin_confirm
import com.tana.spa.feature.admin.resources.admin_confirm_anyway
import com.tana.spa.feature.admin.resources.admin_confirmed_toast
import com.tana.spa.feature.admin.resources.admin_conflict
import com.tana.spa.feature.admin.resources.admin_decline
import com.tana.spa.feature.admin.resources.admin_decline_body
import com.tana.spa.feature.admin.resources.admin_decline_keep
import com.tana.spa.feature.admin.resources.admin_decline_reason_overlap
import com.tana.spa.feature.admin.resources.admin_decline_reason_room
import com.tana.spa.feature.admin.resources.admin_decline_reason_therapist
import com.tana.spa.feature.admin.resources.admin_decline_suggest
import com.tana.spa.feature.admin.resources.admin_decline_suggest_mai
import com.tana.spa.feature.admin.resources.admin_decline_suggest_none
import com.tana.spa.feature.admin.resources.admin_decline_suggest_same_midday
import com.tana.spa.feature.admin.resources.admin_decline_suggest_tomorrow
import com.tana.spa.feature.admin.resources.admin_decline_title
import com.tana.spa.feature.admin.resources.admin_declined_toast
import com.tana.spa.feature.admin.resources.admin_declined_toast_alternative
import com.tana.spa.feature.admin.resources.admin_desk_failed
import com.tana.spa.feature.admin.resources.admin_desk_retry
import com.tana.spa.feature.admin.resources.admin_detail_price
import com.tana.spa.feature.admin.resources.admin_detail_therapist
import com.tana.spa.feature.admin.resources.admin_detail_time
import com.tana.spa.feature.admin.resources.admin_detail_treatment
import com.tana.spa.feature.admin.resources.admin_no_show
import com.tana.spa.feature.admin.resources.admin_no_show_toast
import com.tana.spa.feature.admin.resources.admin_overlap
import com.tana.spa.feature.admin.resources.admin_price
import com.tana.spa.feature.admin.resources.admin_price_code
import com.tana.spa.feature.admin.resources.admin_queue_empty
import com.tana.spa.feature.admin.resources.admin_refused
import com.tana.spa.feature.admin.resources.admin_refused_overlap
import com.tana.spa.feature.admin.resources.admin_requests_subtitle
import com.tana.spa.feature.admin.resources.admin_requests_title
import com.tana.spa.feature.admin.resources.admin_requests_title_accent
import com.tana.spa.feature.admin.resources.admin_schedule_title
import com.tana.spa.feature.admin.resources.admin_service_meta
import com.tana.spa.feature.admin.resources.admin_stale
import com.tana.spa.feature.admin.resources.admin_status_confirmed
import com.tana.spa.feature.admin.resources.admin_status_declined
import com.tana.spa.feature.admin.resources.admin_status_pending
import com.tana.spa.feature.admin.resources.admin_status_recorded
import com.tana.spa.feature.admin.resources.admin_time_range
import com.tana.spa.feature.admin.resources.admin_visit_completed
import com.tana.spa.feature.admin.resources.admin_visit_toast
import com.tana.spa.feature.admin.resources.admin_waited
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.spa.SpaDate
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val QUEUE_WIDTH = 340.dp
private const val MINUTES_PER_HOUR = 60

/** Stateless: renders [state], reports intents through [onEvent], routes one-shot [effects]. */
@Composable
fun AdminRequestsScreen(
    state: AdminRequestsState,
    effects: Flow<AdminRequestsEffect>,
    onEvent: (AdminRequestsEvent) -> Unit,
    onOpenCodes: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenGuestApp: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            val message = when (effect) {
                is AdminRequestsEffect.ShowConfirmed -> getString(Res.string.admin_confirmed_toast, effect.guest)

                is AdminRequestsEffect.ShowDeclined -> if (effect.withAlternative) {
                    getString(Res.string.admin_declined_toast_alternative, effect.guest)
                } else {
                    getString(Res.string.admin_declined_toast, effect.guest)
                }

                is AdminRequestsEffect.ShowVisitRecorded -> getString(
                    if (effect.attended) Res.string.admin_visit_toast else Res.string.admin_no_show_toast,
                    effect.guest,
                )

                is AdminRequestsEffect.ShowRefused ->
                    getString(if (effect.overlap) Res.string.admin_refused_overlap else Res.string.admin_refused)
            }
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    AdminScaffold(
        section = AdminSection.REQUESTS,
        pendingCount = state.pending.size,
        onSelect = {
            when (it) {
                AdminSection.CODES -> onOpenCodes()
                AdminSection.MENU -> onOpenMenu()
                AdminSection.REQUESTS -> Unit
            }
        },
        onOpenGuestApp = onOpenGuestApp,
    ) { wide ->
        Box(Modifier.fillMaxSize()) {
            if (wide) {
                SpaPage(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    maxWidth = SpaPageWidth.Full,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.width(QUEUE_WIDTH), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Header(state)
                            Queue(state, onEvent)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            state.selected?.let { DecisionPanel(it, state.nowEpochMillis, onEvent) }
                            ScheduleSection(state)
                        }
                    }
                }
            } else {
                SpaPage(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    maxWidth = SpaPageWidth.Full,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Header(state)
                    Queue(state, onEvent)
                    state.selected?.let { DecisionPanel(it, state.nowEpochMillis, onEvent) }
                    ScheduleSection(state)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    state.declining?.let { DeclineDialog(it, state.reason, state.alternative, onEvent) }
}

@Composable
private fun Header(state: AdminRequestsState) {
    SpaScreenHeader(
        title = stringResource(Res.string.admin_requests_title),
        accent = stringResource(Res.string.admin_requests_title_accent),
        subtitle = stringResource(
            Res.string.admin_requests_subtitle,
            SpaDate.fromEpochDay(state.dayEpochDay).formatFull(),
            state.pending.size,
        ),
    )
}

@Composable
private fun Queue(state: AdminRequestsState, onEvent: (AdminRequestsEvent) -> Unit) {
    SpaCard(Modifier.fillMaxWidth(), padding = PaddingValues(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when {
                state.failed -> QueueMessage(
                    text = stringResource(Res.string.admin_desk_failed),
                    action = stringResource(Res.string.admin_desk_retry),
                    onAction = { onEvent(AdminRequestsEvent.Refresh) },
                )

                state.loading && state.requests.isEmpty() -> QueueMessage(text = stringResource(Res.string.admin_queue_empty))

                state.requests.isEmpty() -> QueueMessage(text = stringResource(Res.string.admin_queue_empty))

                else -> state.requests.forEach { request ->
                    QueueRow(request, request.id == state.selectedId) {
                        onEvent(AdminRequestsEvent.RequestSelected(request.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) SpaSecondaryButton(text = action, onClick = onAction)
    }
}

@Composable
private fun QueueRow(request: DeskRequest, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = request.guest,
                    style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = waitedLabel(request.waitedMinutes),
                    style = SpaTheme.textStyles.caption,
                    color = if (request.stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${request.serviceName} · ${formatHour(request.startHour)}",
                    style = SpaTheme.textStyles.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(request.status)
                    if (request.conflict != null && request.status == DeskStatus.PENDING) {
                        WarningTag(stringResource(Res.string.admin_overlap))
                    }
                    if (request.stale) WarningTag(stringResource(Res.string.admin_stale))
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            headlineColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .selectable(selected, role = Role.Tab, onClick = onSelect),
    )
}

@Composable
private fun WarningTag(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        SpaIconImage(SpaIcon.Warning, size = 14.dp, tint = MaterialTheme.colorScheme.error)
        Text(
            text = text,
            style = SpaTheme.textStyles.label.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** "9 min ago", then hours once a request has been waiting that long. */
@Composable
private fun waitedLabel(minutes: Int): String = stringResource(
    Res.string.admin_waited,
    if (minutes < MINUTES_PER_HOUR) "$minutes min" else "${minutes / MINUTES_PER_HOUR} h",
)

@Composable
private fun StatusPill(status: DeskStatus) {
    when (status) {
        DeskStatus.PENDING -> SpaStatusPill(
            text = stringResource(Res.string.admin_status_pending),
            container = SpaTheme.extraColors.warningContainer,
            content = SpaTheme.extraColors.warning,
            icon = SpaIcon.Clock,
        )

        DeskStatus.CONFIRMED -> SpaStatusPill(
            text = stringResource(Res.string.admin_status_confirmed),
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.primary,
            icon = SpaIcon.Check,
        )

        DeskStatus.DECLINED, DeskStatus.CANCELLED, DeskStatus.NO_SHOW -> SpaStatusPill(
            text = stringResource(Res.string.admin_status_declined),
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            icon = SpaIcon.Close,
        )

        DeskStatus.COMPLETED -> SpaStatusPill(
            text = stringResource(Res.string.admin_status_recorded),
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurface,
            icon = SpaIcon.CheckCircle,
        )
    }
}

@Composable
private fun DecisionPanel(request: DeskRequest, nowEpochMillis: Long, onEvent: (AdminRequestsEvent) -> Unit) {
    SpaCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = request.guest,
                    style = SpaTheme.textStyles.cardTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(request.reference, style = SpaTheme.textStyles.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusPill(request.status)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailRow(
                    stringResource(Res.string.admin_detail_treatment),
                    stringResource(Res.string.admin_service_meta, request.serviceName, request.minutes),
                )
                DetailRow(stringResource(Res.string.admin_detail_therapist), request.therapistName)
                DetailRow(
                    stringResource(Res.string.admin_detail_time),
                    stringResource(
                        Res.string.admin_time_range,
                        // Day and all: a request can be for any day, not only today.
                        formatDayAndTime(request.startDayEpochDay, request.startHour),
                        formatHour(request.startHour + request.minutes / MINUTES_PER_HOUR.toDouble()),
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(Res.string.admin_detail_price),
                        style = SpaTheme.textStyles.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(96.dp),
                    )
                    Text(
                        text = stringResource(Res.string.admin_price, request.total),
                        style = SpaTheme.textStyles.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val code = request.code
                    if (code != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            SpaIconImage(SpaIcon.Percent, size = 14.dp, tint = MaterialTheme.colorScheme.secondary)
                            Text(
                                text = stringResource(Res.string.admin_price_code, code, request.discount),
                                style = SpaTheme.textStyles.body,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpaIconImage(SpaIcon.Phone, size = 15.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(request.phone, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            request.note?.let { note ->
                NoteBox(
                    icon = SpaIcon.Note,
                    text = note,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (request.conflict != null && request.status == DeskStatus.PENDING) {
                NoteBox(
                    icon = SpaIcon.Warning,
                    text = stringResource(Res.string.admin_conflict, request.conflict.orEmpty()),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            if (request.status == DeskStatus.PENDING) {
                Column(
                    modifier = Modifier.fillMaxWidth().spaPocket().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val clash = request.conflict != null
                    SpaPocketButton(
                        text = stringResource(if (clash) Res.string.admin_confirm_anyway else Res.string.admin_confirm),
                        // A clash is confirmed deliberately: the first press is the owner saying so.
                        onClick = { onEvent(AdminRequestsEvent.Confirmed(request.id, force = clash)) },
                        icon = SpaIcon.Check,
                    )
                    SpaSecondaryButton(
                        text = stringResource(Res.string.admin_decline),
                        onClick = { onEvent(AdminRequestsEvent.DeclineStarted(request.id)) },
                    )
                }
            }

            // Only after the visit was due to start: the server refuses it before then.
            if (request.visitRecordable(nowEpochMillis)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaPrimaryButton(
                        text = stringResource(Res.string.admin_visit_completed),
                        onClick = { onEvent(AdminRequestsEvent.VisitRecorded(request.id, attended = true)) },
                    )
                    SpaSecondaryButton(
                        text = stringResource(Res.string.admin_no_show),
                        onClick = { onEvent(AdminRequestsEvent.VisitRecorded(request.id, attended = false)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = label,
            style = SpaTheme.textStyles.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun NoteBox(icon: SpaIcon, text: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpaIconImage(icon, size = 16.dp, tint = content)
            Text(text, style = SpaTheme.textStyles.body, color = content)
        }
    }
}

@Composable
private fun ScheduleSection(state: AdminRequestsState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.admin_schedule_title),
            style = SpaTheme.textStyles.cardTitle,
            color = MaterialTheme.colorScheme.onBackground,
        )
        TodaySchedule(state.selected, state.confirmed)
    }
}

@Composable
private fun DeclineDialog(
    request: DeskRequest,
    reason: DeclineReasonDto,
    alternative: AlternativeOffer,
    onEvent: (AdminRequestsEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(AdminRequestsEvent.DeclineDismissed) },
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = stringResource(Res.string.admin_decline_title, request.guest),
                style = SpaTheme.textStyles.cardTitle,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.admin_decline_body),
                    style = SpaTheme.textStyles.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ReasonOption(stringResource(Res.string.admin_decline_reason_overlap), reason == DeclineReasonDto.OVERLAP) {
                    onEvent(AdminRequestsEvent.ReasonChosen(DeclineReasonDto.OVERLAP))
                }
                ReasonOption(
                    label = stringResource(Res.string.admin_decline_reason_therapist),
                    selected = reason == DeclineReasonDto.THERAPIST_UNAVAILABLE,
                ) { onEvent(AdminRequestsEvent.ReasonChosen(DeclineReasonDto.THERAPIST_UNAVAILABLE)) }
                ReasonOption(stringResource(Res.string.admin_decline_reason_room), reason == DeclineReasonDto.NO_ROOM) {
                    onEvent(AdminRequestsEvent.ReasonChosen(DeclineReasonDto.NO_ROOM))
                }

                Text(
                    text = stringResource(Res.string.admin_decline_suggest),
                    style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
                AlternativeMenu(alternative) { onEvent(AdminRequestsEvent.AlternativeChosen(it)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(AdminRequestsEvent.DeclineConfirmed) }) {
                Text(
                    text = stringResource(Res.string.admin_decline),
                    style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(AdminRequestsEvent.DeclineDismissed) }) {
                Text(
                    text = stringResource(Res.string.admin_decline_keep),
                    style = SpaTheme.textStyles.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

/** Material menu anchored to a tonal button — the equivalent of the design's select. */
@Composable
private fun AlternativeMenu(selected: AlternativeOffer, onChoose: (AlternativeOffer) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text(alternativeLabel(selected), style = SpaTheme.textStyles.body, modifier = Modifier.weight(1f))
            SpaIconImage(SpaIcon.ChevronDown, size = 16.dp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AlternativeOffer.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(alternativeLabel(option), style = SpaTheme.textStyles.body) },
                    onClick = {
                        onChoose(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun alternativeLabel(option: AlternativeOffer): String = when (option) {
    AlternativeOffer.NONE -> stringResource(Res.string.admin_decline_suggest_none)
    AlternativeOffer.TODAY_MIDDAY -> stringResource(Res.string.admin_decline_suggest_same_midday)
    AlternativeOffer.TODAY_WITH_MAI -> stringResource(Res.string.admin_decline_suggest_mai)
    AlternativeOffer.TOMORROW_MORNING -> stringResource(Res.string.admin_decline_suggest_tomorrow)
}

@Composable
private fun ReasonOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
        Text(label, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurface)
    }
}
