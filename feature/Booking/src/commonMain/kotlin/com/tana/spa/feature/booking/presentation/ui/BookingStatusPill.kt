package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaTone
import com.tana.spa.core.ui.components.SpaTonePill
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.bookings_status_cancelled
import com.tana.spa.feature.booking.resources.bookings_status_completed
import com.tana.spa.feature.booking.resources.bookings_status_confirmed
import com.tana.spa.feature.booking.resources.bookings_status_declined
import com.tana.spa.feature.booking.resources.bookings_status_no_show
import com.tana.spa.feature.booking.resources.bookings_status_pending
import com.tana.spa.shared.booking.BookingStatusDto
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BookingStatusPill(status: BookingStatusDto, modifier: Modifier = Modifier) {
    val (label, tone, icon) = when (status) {
        BookingStatusDto.PENDING -> Triple(Res.string.bookings_status_pending, SpaTone.Pending, SpaIcon.Clock)
        BookingStatusDto.CONFIRMED -> Triple(Res.string.bookings_status_confirmed, SpaTone.Positive, SpaIcon.Check)
        BookingStatusDto.COMPLETED -> Triple(Res.string.bookings_status_completed, SpaTone.Neutral, SpaIcon.CheckCircle)
        BookingStatusDto.DECLINED -> Triple(Res.string.bookings_status_declined, SpaTone.Negative, SpaIcon.Close)
        BookingStatusDto.CANCELLED -> Triple(Res.string.bookings_status_cancelled, SpaTone.Neutral, SpaIcon.Close)
        BookingStatusDto.NO_SHOW -> Triple(Res.string.bookings_status_no_show, SpaTone.Negative, SpaIcon.Prohibit)
    }
    SpaTonePill(text = stringResource(label), tone = tone, icon = icon, modifier = modifier)
}
