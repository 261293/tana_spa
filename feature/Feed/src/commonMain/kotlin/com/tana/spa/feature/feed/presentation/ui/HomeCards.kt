package com.tana.spa.feature.feed.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaCatalogCard
import com.tana.spa.core.ui.components.SpaEyebrow
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaTreatmentCard
import com.tana.spa.feature.feed.presentation.data.UiAppointment
import com.tana.spa.feature.feed.presentation.data.UiProduct
import com.tana.spa.feature.feed.presentation.data.UiService
import com.tana.spa.feature.feed.resources.Res
import com.tana.spa.feature.feed.resources.home_appointment_upcoming
import com.tana.spa.feature.feed.resources.home_combo
import com.tana.spa.feature.feed.resources.home_duration
import com.tana.spa.feature.feed.resources.home_manage_booking
import com.tana.spa.feature.feed.resources.home_next_visit
import org.jetbrains.compose.resources.stringResource

/** The sage panel the design puts between the hero and the treatments. */
@Composable
internal fun NextVisitCard(appointment: UiAppointment, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SpaCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        padding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SpaEyebrow(stringResource(Res.string.home_next_visit))
            Text(
                text = appointment.serviceName,
                style = SpaTheme.textStyles.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.home_appointment_upcoming),
                style = SpaTheme.textStyles.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpaSecondaryButton(
                text = stringResource(Res.string.home_manage_booking),
                onClick = onClick,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
internal fun TreatmentCard(service: UiService, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SpaTreatmentCard(
        title = service.name,
        category = if (service.isCombo) stringResource(Res.string.home_combo) else null,
        priceLabel = service.priceLabel,
        detail = stringResource(Res.string.home_duration, service.durationMinutes),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun ProductCard(product: UiProduct, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SpaCatalogCard(
        title = product.name,
        priceLabel = product.priceLabel,
        onClick = onClick,
        modifier = modifier.width(170.dp),
    )
}
