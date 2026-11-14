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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaSummaryRow
import com.tana.spa.core.ui.components.SpaTextField
import com.tana.spa.core.ui.formatDayAndTime
import com.tana.spa.core.ui.formatHour
import com.tana.spa.core.ui.formatPrice
import com.tana.spa.core.ui.formatRelative
import com.tana.spa.core.ui.localizedEligibleLabel
import com.tana.spa.core.ui.localizedLabel
import com.tana.spa.core.ui.localizedTitle
import com.tana.spa.feature.booking.domain.GuestBooking
import com.tana.spa.feature.booking.presentation.BookingEvent
import com.tana.spa.feature.booking.presentation.BookingState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.booking_code_applied
import com.tana.spa.feature.booking.resources.booking_code_apply
import com.tana.spa.feature.booking.resources.booking_code_empty
import com.tana.spa.feature.booking.resources.booking_code_expired
import com.tana.spa.feature.booking.resources.booking_code_helper
import com.tana.spa.feature.booking.resources.booking_code_label
import com.tana.spa.feature.booking.resources.booking_code_minimum
import com.tana.spa.feature.booking.resources.booking_code_no_service
import com.tana.spa.feature.booking.resources.booking_code_placeholder
import com.tana.spa.feature.booking.resources.booking_code_unknown
import com.tana.spa.feature.booking.resources.booking_code_wrong_category
import com.tana.spa.feature.booking.resources.booking_disclaimer
import com.tana.spa.feature.booking.resources.booking_request
import com.tana.spa.feature.booking.resources.booking_request_need_service
import com.tana.spa.feature.booking.resources.booking_request_need_time
import com.tana.spa.feature.booking.resources.booking_review_not_chosen
import com.tana.spa.feature.booking.resources.booking_review_pick_time
import com.tana.spa.feature.booking.resources.booking_review_treatment
import com.tana.spa.feature.booking.resources.booking_review_when
import com.tana.spa.feature.booking.resources.booking_sent_another
import com.tana.spa.feature.booking.resources.booking_sent_body
import com.tana.spa.feature.booking.resources.booking_sent_bookings
import com.tana.spa.feature.booking.resources.booking_sent_notifications
import com.tana.spa.feature.booking.resources.booking_sent_title
import com.tana.spa.feature.booking.resources.booking_service_meta
import com.tana.spa.feature.booking.resources.booking_subtotal
import com.tana.spa.feature.booking.resources.booking_total_label
import com.tana.spa.shared.spa.PromoCheck
import com.tana.spa.shared.spa.PromoRejection
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Summary, discount code and the moss pocket that closes the booking. */
@Composable
internal fun BookingReview(state: BookingState, onEvent: (BookingEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        val notChosen = stringResource(Res.string.booking_review_not_chosen)
        val service = state.service
        SpaSummaryRow(
            label = stringResource(Res.string.booking_review_treatment),
            value =
            service?.let { "${it.name} · ${stringResource(Res.string.booking_service_meta, it.minutes, it.category.localizedLabel())}" }
                ?: notChosen,
        )
        SpaSummaryRow(
            label = stringResource(Res.string.booking_review_when),
            value = state.slot?.let { "${state.day.formatRelative(state.today)}, ${formatHour(it.startHour)}" }
                ?: stringResource(Res.string.booking_review_pick_time, state.day.formatRelative(state.today)),
        )

        SpaTextField(
            label = stringResource(Res.string.booking_code_label),
            value = state.code,
            onValueChange = { onEvent(BookingEvent.CodeChanged(it)) },
            placeholder = stringResource(Res.string.booking_code_placeholder),
            helper = promoHelper(state.check),
            error = promoError(state.check),
            modifier = Modifier.padding(top = 16.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onEvent(BookingEvent.ApplyCode) }),
            trailing = {
                TextButton(
                    onClick = { onEvent(BookingEvent.ApplyCode) },
                    enabled = state.code.isNotBlank(),
                ) {
                    Text(stringResource(Res.string.booking_code_apply), style = SpaTheme.textStyles.caption)
                }
            },
        )

        val applied = state.check as? PromoCheck.Applied
        Column(Modifier.padding(top = 20.dp)) {
            SpaSummaryRow(
                label = stringResource(Res.string.booking_subtotal),
                value = service?.let { formatPrice(it.priceVnd) } ?: "—",
            )
            applied?.let {
                SpaSummaryRow(
                    label = it.promo.code,
                    value = "−${formatPrice(it.discountVnd)}",
                )
            }
            SpaSummaryRow(
                label = stringResource(Res.string.booking_total_label),
                value = service?.let { formatPrice(state.totalVnd) } ?: "—",
                total = true,
            )
        }

        missingForRequest(state)?.let { missing ->
            Text(
                text = stringResource(missing),
                style = SpaTheme.textStyles.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        SpaPrimaryButton(
            text = stringResource(Res.string.booking_request),
            onClick = { onEvent(BookingEvent.Submit) },
            enabled = state.canRequest,
            loading = state.sending,
            modifier = Modifier.padding(top = 12.dp),
        )

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
private fun promoHelper(check: PromoCheck?): String? = when (check) {
    null -> stringResource(Res.string.booking_code_helper)

    is PromoCheck.Applied -> stringResource(
        Res.string.booking_code_applied,
        check.promo.code,
        check.promo.localizedTitle.lowercase(),
    )

    is PromoCheck.Rejected -> null
}

@Composable
private fun promoError(check: PromoCheck?): String? {
    val rejected = check as? PromoCheck.Rejected ?: return null
    return when (rejected.reason) {
        PromoRejection.EMPTY -> stringResource(Res.string.booking_code_empty)

        PromoRejection.UNKNOWN_CODE -> stringResource(Res.string.booking_code_unknown, rejected.code)

        PromoRejection.EXPIRED -> stringResource(Res.string.booking_code_expired, rejected.code)

        PromoRejection.NO_SERVICE_CHOSEN -> stringResource(Res.string.booking_code_no_service)

        PromoRejection.WRONG_CATEGORY -> stringResource(
            Res.string.booking_code_wrong_category,
            rejected.code,
            rejected.promo?.localizedEligibleLabel().orEmpty(),
        )

        PromoRejection.BELOW_MINIMUM_SPEND -> stringResource(
            Res.string.booking_code_minimum,
            rejected.code,
            formatPrice(rejected.promo?.minSpendVnd ?: 0),
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
                modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SpaIconImage(SpaIcon.CheckCircle, size = 32.dp, tint = MaterialTheme.colorScheme.secondary)
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
                text = stringResource(
                    Res.string.booking_sent_body,
                    sent.serviceName,
                    formatDayAndTime(sent.startDayEpochDay, sent.startHour),
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

/** Why the request cannot be sent yet, so the disabled button is never left unexplained. */
private fun missingForRequest(state: BookingState): StringResource? = when {
    state.service == null -> Res.string.booking_request_need_service
    state.slot == null -> Res.string.booking_request_need_time
    else -> null
}
