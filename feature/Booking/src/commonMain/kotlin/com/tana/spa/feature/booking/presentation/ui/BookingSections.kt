package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPebbleShape
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.SpaWindowSize
import com.tana.spa.core.ui.components.SpaAvatar
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaFilterChip
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaImageTile
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaSkeleton
import com.tana.spa.core.ui.formatDayChip
import com.tana.spa.core.ui.formatHour
import com.tana.spa.core.ui.formatRelativeDayAndTime
import com.tana.spa.core.ui.weekdayShort
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
import com.tana.spa.feature.booking.resources.booking_service_details
import com.tana.spa.feature.booking.resources.booking_service_meta
import com.tana.spa.feature.booking.resources.booking_slot_taken
import com.tana.spa.feature.booking.resources.booking_step_done
import com.tana.spa.feature.booking.resources.booking_therapist_next
import com.tana.spa.shared.spa.Service
import com.tana.spa.shared.spa.SpaCatalog
import com.tana.spa.shared.spa.SpaDate
import com.tana.spa.shared.spa.Therapist
import com.tana.spa.shared.spa.initialsOf
import org.jetbrains.compose.resources.stringResource

private val ServiceTileShape = RoundedCornerShape(topStart = 22.dp, topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 22.dp)
private val SERVICE_ROW_HEIGHT = 88.dp
private val THERAPIST_CARD_HEIGHT = 108.dp
private val SLOT_ROW_HEIGHT = 40.dp
private const val SERVICE_PLACEHOLDERS = 4
private const val THERAPIST_PLACEHOLDER_ROWS = 2
private const val SLOT_PLACEHOLDER_ROWS = 3
private const val DAYS_ON_RAIL = 14
private const val BOOKING_HORIZON_DAYS = 60
private const val MILLIS_PER_DAY = 86_400_000L

/** Material's own calendar width (`DatePickerModalTokens.ContainerWidth`): 7 cells of 48.dp plus 12.dp either side. */
private val CALENDAR_WIDTH = 360.dp

/** Below this the dates stop being readable; no window this app runs in is that narrow. */
private const val MIN_CALENDAR_SCALE = 0.5f

@Composable
internal fun StepHeader(number: Int, title: String, done: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(
                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                SpaIconImage(
                    SpaIcon.Check,
                    size = 15.dp,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    contentDescription = stringResource(Res.string.booking_step_done),
                )
            } else {
                Text(
                    text = number.toString(),
                    style = SpaTheme.textStyles.label.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = title,
            style = SpaTheme.textStyles.cardTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * The menu: one column on a phone, [columns] columns that each flow on their own once there is
 * room, so the card that grows when it is picked pushes only the cards under it.
 */
@Composable
internal fun ServiceList(
    state: BookingState,
    onSelect: (String) -> Unit,
    onDetails: (String) -> Unit,
    onRetry: () -> Unit,
    columns: Int = 1,
    modifier: Modifier = Modifier,
) {
    when {
        state.catalogLoading -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(SERVICE_PLACEHOLDERS) { SpaSkeleton(SERVICE_ROW_HEIGHT) }
        }

        state.catalogFailed -> Column(modifier) { MenuUnavailable(onRetry) }

        else -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            staggeredColumns(state.services, columns).forEach { column ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    column.forEach { service ->
                        ServiceRow(
                            service = service,
                            selected = service.id == state.serviceId,
                            onSelect = { onSelect(service.id) },
                            onDetails = { onDetails(service.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Treatments dealt into [columns] columns, each flowing on its own. Round robin, so reading across
 * the top of the grid still gives the first treatments in the order the spa listed them.
 */
internal fun <T> staggeredColumns(items: List<T>, columns: Int): List<List<T>> {
    val count = columns.coerceAtLeast(1)
    return List(count) { column -> items.filterIndexed { index, _ -> index % count == column } }
}

/** One column on a phone and on a tablet in portrait; the grid starts where two fit side by side. */
internal fun catalogueColumns(size: SpaWindowSize): Int = when (size) {
    SpaWindowSize.COMPACT, SpaWindowSize.MEDIUM -> 1
    SpaWindowSize.EXPANDED -> 2
    SpaWindowSize.LARGE -> 3
}

@Composable
private fun MenuUnavailable(onRetry: () -> Unit) {
    SpaCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.booking_menu_failed),
                style = SpaTheme.textStyles.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpaSecondaryButton(text = stringResource(Res.string.booking_menu_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun ServiceRow(service: Service, selected: Boolean, onSelect: () -> Unit, onDetails: () -> Unit) {
    SpaCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SpaPebbleShape,
        color = Color.Transparent,
        padding = PaddingValues(0.dp),
        onClick = onSelect,
        selected = selected,
    ) {
        Column {
            ListItem(
                headlineContent = {
                    Text(service.name, style = SpaTheme.textStyles.cardTitle, color = MaterialTheme.colorScheme.onBackground)
                },
                supportingContent = {
                    Text(
                        text = stringResource(Res.string.booking_service_meta, service.minutes, service.category.label),
                        style = SpaTheme.textStyles.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingContent = { SpaImageTile(Modifier.size(64.dp), shape = ServiceTileShape) },
                trailingContent = {
                    Text(
                        text = stringResource(Res.string.booking_price, service.priceUsd),
                        style = SpaTheme.textStyles.cardTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            AnimatedVisibility(selected) {
                Column(
                    modifier = Modifier.padding(start = 80.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpaIconImage(SpaIcon.Leaf, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
                        Text(service.includes, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurface)
                    }
                    // The whole card is the choice, so the detail page needs a target of its own.
                    SpaSecondaryButton(text = stringResource(Res.string.booking_service_details), onClick = onDetails)
                }
            }
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
internal fun TherapistPicker(state: BookingState, onSelect: (String) -> Unit, columns: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.catalogLoading) {
            repeat(THERAPIST_PLACEHOLDER_ROWS) { SpaSkeleton(THERAPIST_CARD_HEIGHT) }
            return@Column
        }
        val choices = listOf(anyTherapist()) + state.therapists
        choices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { therapist ->
                    TherapistCard(
                        therapist = therapist,
                        today = state.today,
                        selected = therapist.id == state.therapistId,
                        onSelect = { onSelect(therapist.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
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

@Composable
private fun TherapistCard(therapist: Therapist, today: SpaDate, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    SpaCard(modifier = modifier, padding = PaddingValues(12.dp), onClick = onSelect, selected = selected) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (therapist.anyAvailable) {
                Box(
                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    SpaIconImage(SpaIcon.Clock, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                SpaAvatar(initialsOf(therapist.name), container = MaterialTheme.colorScheme.background)
            }
            Spacer(Modifier.height(6.dp))
            Text(therapist.name, style = SpaTheme.textStyles.cardTitle, color = MaterialTheme.colorScheme.onBackground)
            Text(therapist.focus, style = SpaTheme.textStyles.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            therapist.nextSlot?.let { next ->
                Text(
                    text = stringResource(
                        Res.string.booking_therapist_next,
                        formatRelativeDayAndTime(next.dayEpochDay, next.hour, today),
                    ),
                    style = SpaTheme.textStyles.caption.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Two weeks of days as filter chips; a day the spa is full is a disabled chip. */
@Composable
internal fun DateRail(selected: SpaDate, today: SpaDate, onSelect: (SpaDate) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(DAYS_ON_RAIL) { index ->
            val date = today.plusDays(index)
            val full = SpaCatalog.isDayFull(date)
            val label = date.formatDayChip(today)
            SpaFilterChip(
                label = if (full) stringResource(Res.string.booking_day_full, label) else label,
                selected = date == selected,
                enabled = !full,
                onClick = { onSelect(date) },
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
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val scale = calendarScale(maxWidth)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            DatePicker(
                state = state,
                modifier = Modifier.fillMaxWidth(),
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
    }
}

/**
 * Material lays the calendar out as seven 48.dp cells plus its own padding and never shrinks below
 * that, so a card narrower than [CALENDAR_WIDTH] clips the last column — Sunday. Scaling the
 * density instead shrinks the whole grid, cells, gaps and labels together.
 */
internal fun calendarScale(available: Dp): Float = (available / CALENDAR_WIDTH).coerceIn(MIN_CALENDAR_SCALE, 1f)

/**
 * The day's open times, as the spa's diary has them: a time it did not offer cannot be chosen,
 * and a taken one is a disabled chip.
 */
@Composable
internal fun SlotGrid(state: BookingState, onSelect: (Long) -> Unit, columns: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            state.slotsLoading && state.slots.isEmpty() -> repeat(SLOT_PLACEHOLDER_ROWS) { SpaSkeleton(SLOT_ROW_HEIGHT) }

            state.slots.isEmpty() -> Text(
                text = stringResource(Res.string.booking_no_times),
                style = SpaTheme.textStyles.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> state.slots.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { slot ->
                        val isSelected = slot.startAtEpochMillis == state.slotAtEpochMillis
                        val time = formatHour(slot.startHour)
                        SpaFilterChip(
                            label = if (slot.available) time else stringResource(Res.string.booking_slot_taken, time),
                            selected = isSelected,
                            enabled = slot.available,
                            onClick = { onSelect(slot.startAtEpochMillis) },
                            leadingIcon = if (isSelected) SpaIcon.Check else null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
