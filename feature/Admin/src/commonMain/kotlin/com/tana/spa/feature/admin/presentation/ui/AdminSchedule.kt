package com.tana.spa.feature.admin.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaAvatar
import com.tana.spa.core.ui.components.spaCork
import com.tana.spa.core.ui.components.spaDashedBorder
import com.tana.spa.feature.admin.resources.Res
import com.tana.spa.feature.admin.resources.admin_block_meta
import com.tana.spa.feature.admin.resources.admin_block_range
import com.tana.spa.feature.admin.resources.admin_overlap_block
import com.tana.spa.feature.admin.resources.admin_proposed
import com.tana.spa.shared.spa.BookingRequest
import com.tana.spa.shared.spa.RequestStatus
import com.tana.spa.shared.spa.SpaCatalog
import com.tana.spa.shared.spa.formatHour
import com.tana.spa.shared.spa.initialsOf
import org.jetbrains.compose.resources.stringResource

private const val DAY_START_HOUR = 9
private const val DAY_END_HOUR = 21
private val HOUR_HEIGHT = 52.dp
private val GUTTER_WIDTH = 56.dp
private val COLUMN_WIDTH = 220.dp

/** The day as the front desk reads it: one column per therapist, the proposed slot dashed on top. */
@Composable
internal fun TodaySchedule(selected: BookingRequest?, confirmed: List<BookingRequest>, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(GUTTER_WIDTH).padding(top = 36.dp)) {
            for (hour in DAY_START_HOUR until DAY_END_HOUR) {
                Box(Modifier.height(HOUR_HEIGHT)) {
                    Text(
                        text = formatHour(hour.toDouble()).replace(":00", ""),
                        style = SpaTheme.textStyles.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.offset(y = (-7).dp),
                    )
                }
            }
        }
        SpaCatalog.staff.forEach { therapist ->
            Column(Modifier.width(COLUMN_WIDTH).padding(end = 8.dp)) {
                Row(
                    modifier = Modifier.height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpaAvatar(initialsOf(therapist.name), size = 28.dp)
                    Text(
                        text = therapist.name,
                        style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                TherapistColumn(therapist.id, selected, confirmed)
            }
        }
    }
}

@Composable
private fun TherapistColumn(therapistId: String, selected: BookingRequest?, confirmed: List<BookingRequest>) {
    val ruleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val booked = SpaCatalog.scheduled.filter { it.therapistId == therapistId }
    val alsoBooked = confirmed.filter { it.therapistId == therapistId }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HOUR_HEIGHT * (DAY_END_HOUR - DAY_START_HOUR))
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .drawBehind {
                val step = HOUR_HEIGHT.toPx()
                var y = step
                while (y < size.height) {
                    drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
            },
    ) {
        booked.forEach { booking ->
            val service = SpaCatalog.service(booking.serviceId) ?: return@forEach
            ScheduleBlock(
                startHour = booking.startHour,
                minutes = service.minutes,
                title = booking.guest,
                subtitle = stringResource(Res.string.admin_block_meta, formatHour(booking.startHour), service.name),
            )
        }
        alsoBooked.forEach { booking ->
            val service = SpaCatalog.service(booking.serviceId) ?: return@forEach
            ScheduleBlock(
                startHour = booking.startHour,
                minutes = service.minutes,
                title = booking.guest,
                subtitle = stringResource(Res.string.admin_block_meta, formatHour(booking.startHour), service.name),
            )
        }
        if (selected != null && selected.status == RequestStatus.PENDING && selected.therapistId == therapistId) {
            ProposedBlock(selected)
        }
    }
}

@Composable
private fun ScheduleBlock(startHour: Double, minutes: Int, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .offset(y = HOUR_HEIGHT * (startHour - DAY_START_HOUR).toFloat() + 2.dp)
            .height(HOUR_HEIGHT * (minutes / 60f) - 4.dp)
            .spaCork(MaterialTheme.shapes.extraSmall, MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = title,
            style = SpaTheme.textStyles.label.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = SpaTheme.textStyles.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProposedBlock(request: BookingRequest) {
    val service = SpaCatalog.service(request.serviceId) ?: return
    val conflicted = request.conflict != null
    val accent = if (conflicted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .offset(y = HOUR_HEIGHT * (request.startHour - DAY_START_HOUR).toFloat() + 2.dp)
            .height(HOUR_HEIGHT * (service.minutes / 60f) - 4.dp)
            .background(accent.copy(alpha = 0.12f), MaterialTheme.shapes.extraSmall)
            .spaDashedBorder(accent, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (conflicted) {
                stringResource(Res.string.admin_overlap_block, request.guest)
            } else {
                stringResource(Res.string.admin_proposed, request.guest)
            },
            style = SpaTheme.textStyles.label.copy(fontWeight = FontWeight.Bold),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                Res.string.admin_block_range,
                formatHour(request.startHour),
                formatHour(request.startHour + service.minutes / 60.0),
            ),
            style = SpaTheme.textStyles.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
