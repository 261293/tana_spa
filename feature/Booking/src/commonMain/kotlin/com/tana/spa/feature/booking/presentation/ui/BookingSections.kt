package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaAvatar
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaChoiceRow
import com.tana.spa.core.ui.components.SpaDateCell
import com.tana.spa.core.ui.components.SpaEyebrow
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaImageTile
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaSkeleton
import com.tana.spa.core.ui.components.SpaSlotButton
import com.tana.spa.core.ui.formatHour
import com.tana.spa.core.ui.formatRelativeDayAndTime
import com.tana.spa.core.ui.monthShort
import com.tana.spa.core.ui.weekdayShort
import com.tana.spa.feature.booking.domain.Slot
import com.tana.spa.feature.booking.presentation.ANY_THERAPIST
import com.tana.spa.feature.booking.presentation.BookingState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.booking_any_therapist
import com.tana.spa.feature.booking.resources.booking_any_therapist_focus
import com.tana.spa.feature.booking.resources.booking_day_full
import com.tana.spa.feature.booking.resources.booking_menu_failed
import com.tana.spa.feature.booking.resources.booking_menu_retry
import com.tana.spa.feature.booking.resources.booking_no_times
import com.tana.spa.feature.booking.resources.booking_price
import com.tana.spa.feature.booking.resources.booking_service_meta
import com.tana.spa.feature.booking.resources.booking_slot_taken
import com.tana.spa.feature.booking.resources.booking_slots_afternoon
import com.tana.spa.feature.booking.resources.booking_slots_evening
import com.tana.spa.feature.booking.resources.booking_slots_morning
import com.tana.spa.feature.booking.resources.booking_therapist_next
import com.tana.spa.feature.booking.resources.booking_today
import com.tana.spa.shared.spa.Service
import com.tana.spa.shared.spa.SpaCatalog
import com.tana.spa.shared.spa.SpaDate
import com.tana.spa.shared.spa.Therapist
import com.tana.spa.shared.spa.initialsOf
import org.jetbrains.compose.resources.stringResource

private val PickThumbShape = RoundedCornerShape(10.dp)
private val PICK_THUMB_SIZE = 56.dp
private val SERVICE_ROW_HEIGHT = 80.dp
private val THERAPIST_ROW_HEIGHT = 72.dp
private val SLOT_ROW_HEIGHT = 46.dp
private const val SERVICE_PLACEHOLDERS = 4
private const val THERAPIST_PLACEHOLDER_ROWS = 2
private const val SLOT_PLACEHOLDER_ROWS = 3
private const val DAYS_ON_RAIL = 14
private const val BOOKING_HORIZON_DAYS = 60
private const val MILLIS_PER_DAY = 86_400_000L
private const val SLOT_COLUMNS = 3
private const val NOON = 12.0
private const val EVENING = 17.0

@Composable
internal fun ServiceList(state: BookingState, onSelect: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        when {
            state.catalogLoading -> repeat(SERVICE_PLACEHOLDERS) { SpaSkeleton(SERVICE_ROW_HEIGHT) }

            state.catalogFailed -> MenuUnavailable(onRetry)

            else -> state.services.forEachIndexed { index, service ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PickRow(service, service.id == state.serviceId) { onSelect(service.id) }
            }
        }
    }
}

@Composable
private fun MenuUnavailable(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.booking_menu_failed),
            style = SpaTheme.textStyles.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SpaSecondaryButton(text = stringResource(Res.string.booking_menu_retry), onClick = onRetry)
    }
}

/** The design's pick row: a square thumb, the treatment and its length, then the price. */
@Composable
private fun PickRow(service: Service, selected: Boolean, onSelect: () -> Unit) {
    val shape = MaterialTheme.shapes.extraSmall
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent, shape)
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpaImageTile(Modifier.size(PICK_THUMB_SIZE), shape = PickThumbShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = service.name,
                style = SpaTheme.textStyles.body,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.booking_service_meta, service.minutes, service.category.label),
                style = SpaTheme.textStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(Res.string.booking_price, service.priceUsd),
            style = SpaTheme.textStyles.cardTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            SpaIconImage(SpaIcon.Check, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * "First available" leads, because it is the fastest way to a confirmed time; it is a scheduling
 * choice rather than a person, so it carries no slug and the spa assigns whoever is free.
 *
 * Empty after a failed load: one request fetches both halves of the menu, and its retry sits with
 * the treatments.
 */
@Composable
internal fun TherapistPicker(state: BookingState, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.catalogLoading) {
            repeat(THERAPIST_PLACEHOLDER_ROWS) { SpaSkeleton(THERAPIST_ROW_HEIGHT) }
            return@Column
        }
        (listOf(anyTherapist()) + state.therapists).forEach { therapist ->
            SpaChoiceRow(
                title = therapist.name,
                subtitle = therapistSubtitle(therapist, state.today),
                selected = therapist.id == state.therapistId,
                onClick = { onSelect(therapist.id) },
                leading = { TherapistBadge(therapist) },
            )
        }
    }
}

@Composable
private fun therapistSubtitle(therapist: Therapist, today: SpaDate): String {
    val next = therapist.nextSlot ?: return therapist.focus
    val nextAt = formatRelativeDayAndTime(next.dayEpochDay, next.hour, today)
    return "${therapist.focus} · ${stringResource(Res.string.booking_therapist_next, nextAt)}"
}

@Composable
private fun TherapistBadge(therapist: Therapist) {
    if (therapist.anyAvailable) {
        Box(
            modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SpaIconImage(SpaIcon.Clock, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        }
    } else {
        SpaAvatar(initialsOf(therapist.name), size = 44.dp, container = MaterialTheme.colorScheme.secondaryContainer)
    }
}

@Composable
private fun anyTherapist() = Therapist(
    id = ANY_THERAPIST,
    name = stringResource(Res.string.booking_any_therapist),
    focus = stringResource(Res.string.booking_any_therapist_focus),
    nextSlot = null,
    anyAvailable = true,
)

/** Two weeks of days as the design's date cells; a day the spa is full cannot be chosen. */
@Composable
internal fun DateRail(selected: SpaDate, today: SpaDate, onSelect: (SpaDate) -> Unit, modifier: Modifier = Modifier) {
    val todayLabel = stringResource(Res.string.booking_today)
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(DAYS_ON_RAIL) { index ->
            val date = today.plusDays(index)
            val full = SpaCatalog.isDayFull(date)
            val label = "${date.weekdayShort()} ${date.day} ${date.monthShort()}"
            SpaDateCell(
                weekday = if (date == today) todayLabel else date.weekdayShort(),
                day = date.day.toString(),
                month = date.monthShort(),
                selected = date == selected,
                enabled = !full,
                onClick = { onSelect(date) },
                contentDescription = if (full) stringResource(Res.string.booking_day_full, label) else null,
            )
        }
    }
}

/**
 * Material's date picker, headless: the step already has its own heading, and the closed days,
 * booked-out days and the sixty-day horizon are expressed as [SelectableDates].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthCalendar(selected: SpaDate, today: SpaDate, onSelect: (SpaDate) -> Unit, modifier: Modifier = Modifier) {
    val bookable = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = SpaDate.fromEpochDay((utcTimeMillis / MILLIS_PER_DAY).toInt())
                return date >= today &&
                    date <= today.plusDays(BOOKING_HORIZON_DAYS) &&
                    !SpaCatalog.isDayFull(date)
            }

            override fun isSelectableYear(year: Int) = year >= today.year
        }
    }
    // The picker keeps the bounds it was built with, so it is rebuilt when the spa names its day.
    val state = key(today) {
        rememberDatePickerState(
            initialSelectedDateMillis = selected.epochDay * MILLIS_PER_DAY,
            selectableDates = bookable,
        )
    }
    LaunchedEffect(state.selectedDateMillis) {
        state.selectedDateMillis
            ?.let { SpaDate.fromEpochDay((it / MILLIS_PER_DAY).toInt()) }
            ?.takeIf { it != selected }
            ?.let(onSelect)
    }
    DatePicker(
        state = state,
        modifier = modifier,
        title = null,
        headline = null,
        showModeToggle = false,
        colors = DatePickerDefaults.colors(
            containerColor = Color.Transparent,
            selectedDayContainerColor = MaterialTheme.colorScheme.primary,
            selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
            todayContentColor = MaterialTheme.colorScheme.primary,
            todayDateBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/**
 * The day's open times, grouped the way the design reads them, as the spa's diary has them: a
 * time it did not offer cannot be chosen, and a taken one is a disabled slot.
 */
@Composable
internal fun SlotGrid(state: BookingState, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            state.slotsLoading && state.slots.isEmpty() -> repeat(SLOT_PLACEHOLDER_ROWS) { SpaSkeleton(SLOT_ROW_HEIGHT) }

            state.slots.isEmpty() -> Text(
                text = stringResource(Res.string.booking_no_times),
                style = SpaTheme.textStyles.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> slotGroups(state.slots).forEach { (label, slots) ->
                SlotGroup(label, slots, state.slotAtEpochMillis, onSelect)
            }
        }
    }
}

@Composable
private fun SlotGroup(label: String, slots: List<Slot>, chosen: Long?, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpaEyebrow(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        slots.chunked(SLOT_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { slot ->
                    val time = formatHour(slot.startHour)
                    SpaSlotButton(
                        label = if (slot.available) time else stringResource(Res.string.booking_slot_taken, time),
                        selected = slot.startAtEpochMillis == chosen,
                        enabled = slot.available,
                        onClick = { onSelect(slot.startAtEpochMillis) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(SLOT_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun slotGroups(slots: List<Slot>): List<Pair<String, List<Slot>>> {
    val morning = stringResource(Res.string.booking_slots_morning)
    val afternoon = stringResource(Res.string.booking_slots_afternoon)
    val evening = stringResource(Res.string.booking_slots_evening)
    return listOf(
        morning to slots.filter { it.startHour < NOON },
        afternoon to slots.filter { it.startHour >= NOON && it.startHour < EVENING },
        evening to slots.filter { it.startHour >= EVENING },
    ).filter { it.second.isNotEmpty() }
}

/** The retry card the wide layout still shows beside the menu. */
@Composable
internal fun CatalogRetryCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    SpaCard(modifier = modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) { MenuUnavailable(onRetry) }
}
