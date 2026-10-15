package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaPocketButton
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaTextField
import com.tana.spa.core.ui.components.spaPocket
import com.tana.spa.feature.booking.domain.GuestBooking
import com.tana.spa.feature.booking.presentation.BookingEvent
import com.tana.spa.feature.booking.presentation.BookingState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.booking_code_applied
import com.tana.spa.feature.booking.resources.booking_code_apply
import com.tana.spa.feature.booking.resources.booking_code_empty
import com.tana.spa.feature.booking.resources.booking_code_helper
import com.tana.spa.feature.booking.resources.booking_code_label
import com.tana.spa.feature.booking.resources.booking_code_minimum
import com.tana.spa.feature.booking.resources.booking_code_no_service
import com.tana.spa.feature.booking.resources.booking_code_placeholder
import com.tana.spa.feature.booking.resources.booking_code_unknown
import com.tana.spa.feature.booking.resources.booking_code_wrong_category
import com.tana.spa.feature.booking.resources.booking_disclaimer
import com.tana.spa.feature.booking.resources.booking_price
import com.tana.spa.feature.booking.resources.booking_request
import com.tana.spa.feature.booking.resources.booking_review_any_therapist
import com.tana.spa.feature.booking.resources.booking_review_not_chosen
import com.tana.spa.feature.booking.resources.booking_review_pick_time
import com.tana.spa.feature.booking.resources.booking_review_therapist
import com.tana.spa.feature.booking.resources.booking_review_treatment
import com.tana.spa.feature.booking.resources.booking_review_when
import com.tana.spa.feature.booking.resources.booking_sent_another
import com.tana.spa.feature.booking.resources.booking_sent_body
import com.tana.spa.feature.booking.resources.booking_sent_bookings
import com.tana.spa.feature.booking.resources.booking_sent_notifications
import com.tana.spa.feature.booking.resources.booking_sent_title
import com.tana.spa.feature.booking.resources.booking_service_meta
import com.tana.spa.feature.booking.resources.booking_total_label
import com.tana.spa.shared.spa.PromoCheck
import com.tana.spa.shared.spa.PromoRejection
import com.tana.spa.shared.spa.eligibleLabel
import com.tana.spa.shared.spa.formatLong
import org.jetbrains.compose.resources.stringResource

/** Summary, discount code and the moss pocket that closes the booking. */
@Composable
internal fun BookingReview(state: BookingState, onEvent: (BookingEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        val notChosen = stringResource(Res.string.booking_review_not_chosen)
        val service = state.service
        SummaryRow(
            label = stringResource(Res.string.booking_review_treatment),
            value = service?.let { "${it.name} · ${stringResource(Res.string.booking_service_meta, it.minutes, it.category.label)}" },
            fallback = notChosen,
        )
        SummaryRow(
            label = stringResource(Res.string.booking_review_therapist),
            value = state.therapist?.name ?: stringResource(Res.string.booking_review_any_therapist),
            fallback = notChosen,
        )
        SummaryRow(
            label = stringResource(Res.string.booking_review_when),
            value = state.slot?.let { "${state.day.formatLong()}, ${it.label}" },
            fallback = stringResource(Res.string.booking_review_pick_time, state.day.formatLong()),
        )

        val applied = state.check as? PromoCheck.Applied
        SpaTextField(
            label = stringResource(Res.string.booking_code_label),
            value = state.code,
            onValueChange = { onEvent(BookingEvent.CodeChanged(it)) },
            placeholder = stringResource(Res.string.booking_code_placeholder),
            helper = promoHelper(state.check),
            error = promoError(state.check),
            modifier = Modifier.padding(top = 16.dp),
            trailing = {
                TextButton(
                    onClick = { onEvent(BookingEvent.ApplyCode) },
                    enabled = state.code.isNotBlank(),
                ) {
                    Text(stringResource(Res.string.booking_code_apply), style = SpaTheme.textStyles.caption)
                }
            },
        )

        Column(
            modifier = Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .spaPocket()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PocketRow(
                label = stringResource(Res.string.booking_review_treatment),
                value = service?.let { stringResource(Res.string.booking_price, it.priceUsd) } ?: "—",
                color = SpaTheme.extraColors.onPocketMuted,
            )
            if (applied != null) {
                PocketRow(
                    label = applied.promo.code,
                    value = "−${stringResource(Res.string.booking_price, applied.discountUsd)}",
                    color = SpaTheme.extraColors.pocketAction,
                    icon = SpaIcon.Percent,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(Res.string.booking_total_label),
                    style = SpaTheme.textStyles.caption,
                    color = SpaTheme.extraColors.onPocketMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = service?.let { stringResource(Res.string.booking_price, state.totalUsd) } ?: "—",
                    style = SpaTheme.textStyles.displayTitle,
                    color = SpaTheme.extraColors.onPocket,
                )
            }
            SpaPocketButton(
                text = stringResource(Res.string.booking_request),
                onClick = { onEvent(BookingEvent.Submit) },
                enabled = state.canRequest,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpaIconImage(SpaIcon.Info, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(Res.string.booking_disclaimer),
                style = SpaTheme.textStyles.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String?, fallback: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value ?: fallback,
            style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PocketRow(label: String, value: String, color: Color, icon: SpaIcon? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            SpaIconImage(it, size = 15.dp, tint = color)
            Box(Modifier.size(6.dp))
        }
        Text(label, style = SpaTheme.textStyles.body, color = color, modifier = Modifier.weight(1f))
        Text(value, style = SpaTheme.textStyles.body, color = color)
    }
}

@Composable
private fun promoHelper(check: PromoCheck?): String? = when (check) {
    null -> stringResource(Res.string.booking_code_helper)

    is PromoCheck.Applied -> stringResource(
        Res.string.booking_code_applied,
        check.promo.code,
        check.promo.title.lowercase(),
    )

    is PromoCheck.Rejected -> null
}

@Composable
private fun promoError(check: PromoCheck?): String? {
    val rejected = check as? PromoCheck.Rejected ?: return null
    return when (rejected.reason) {
        PromoRejection.EMPTY -> stringResource(Res.string.booking_code_empty)

        PromoRejection.UNKNOWN_CODE -> stringResource(Res.string.booking_code_unknown, rejected.code)

        PromoRejection.NO_SERVICE_CHOSEN -> stringResource(Res.string.booking_code_no_service)

        PromoRejection.WRONG_CATEGORY -> stringResource(
            Res.string.booking_code_wrong_category,
            rejected.code,
            rejected.promo?.eligibleLabel().orEmpty(),
        )

        PromoRejection.BELOW_MINIMUM_SPEND -> stringResource(
            Res.string.booking_code_minimum,
            rejected.code,
            rejected.promo?.minSpendUsd ?: 0,
        )
    }
}

@Composable
internal fun RequestSentDialog(sent: GuestBooking, onEvent: (BookingEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(BookingEvent.DismissSent) },
        containerColor = MaterialTheme.colorScheme.background,
        icon = {
            Box(
                modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SpaIconImage(SpaIcon.CheckCircle, size = 32.dp, tint = MaterialTheme.colorScheme.primary)
            }
        },
        title = {
            Text(
                text = stringResource(Res.string.booking_sent_title),
                style = SpaTheme.textStyles.screenTitle,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                // The spa's own words for it: with "any therapist" the guest learns here who took them.
                text = stringResource(
                    Res.string.booking_sent_body,
                    sent.serviceName,
                    sent.therapistName,
                    sent.startLabel,
                ),
                style = SpaTheme.textStyles.bodyRelaxed,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpaPrimaryButton(
                    text = stringResource(Res.string.booking_sent_bookings),
                    onClick = { onEvent(BookingEvent.OpenBookings) },
                )
                SpaSecondaryButton(
                    text = stringResource(Res.string.booking_sent_notifications),
                    onClick = { onEvent(BookingEvent.OpenNotifications) },
                )
                TextButton(
                    onClick = { onEvent(BookingEvent.DismissSent) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(Res.string.booking_sent_another), style = SpaTheme.textStyles.body)
                }
            }
        },
    )
}
