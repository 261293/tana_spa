package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaFilterChip
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaSkeleton
import com.tana.spa.core.ui.components.SpaTextField
import com.tana.spa.core.ui.components.SpaTreatmentCard
import com.tana.spa.feature.booking.presentation.TreatmentsEffect
import com.tana.spa.feature.booking.presentation.TreatmentsEvent
import com.tana.spa.feature.booking.presentation.TreatmentsState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.booking_menu_failed
import com.tana.spa.feature.booking.resources.booking_menu_retry
import com.tana.spa.feature.booking.resources.booking_price
import com.tana.spa.feature.booking.resources.service_minutes
import com.tana.spa.feature.booking.resources.treatments_count
import com.tana.spa.feature.booking.resources.treatments_empty_action
import com.tana.spa.feature.booking.resources.treatments_empty_body
import com.tana.spa.feature.booking.resources.treatments_empty_title
import com.tana.spa.feature.booking.resources.treatments_eyebrow
import com.tana.spa.feature.booking.resources.treatments_filter_all
import com.tana.spa.feature.booking.resources.treatments_note
import com.tana.spa.feature.booking.resources.treatments_search_label
import com.tana.spa.feature.booking.resources.treatments_search_placeholder
import com.tana.spa.feature.booking.resources.treatments_title
import com.tana.spa.shared.spa.Service
import com.tana.spa.shared.spa.ServiceCategory
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

private const val CARD_PLACEHOLDERS = 3
private val CARD_PLACEHOLDER_HEIGHT = 280.dp

/** Stateless: renders [state], reports intents through [onEvent], routes one-shot [effects]. */
@Composable
fun TreatmentsScreen(
    state: TreatmentsState,
    effects: Flow<TreatmentsEffect>,
    onEvent: (TreatmentsEvent) -> Unit,
    onOpenTreatment: (String) -> Unit,
) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is TreatmentsEffect.OpenTreatment -> onOpenTreatment(effect.serviceId)
            }
        }
    }

    SpaPage(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        maxWidth = SpaPageWidth.Reading,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SpaScreenHeader(
            eyebrow = stringResource(Res.string.treatments_eyebrow),
            title = stringResource(Res.string.treatments_title),
            subtitle = stringResource(Res.string.treatments_note),
        )

        SpaTextField(
            label = stringResource(Res.string.treatments_search_label),
            value = state.query,
            onValueChange = { onEvent(TreatmentsEvent.QueryChanged(it)) },
            placeholder = stringResource(Res.string.treatments_search_placeholder),
        )

        CategoryRail(state.category) { onEvent(TreatmentsEvent.CategoryChosen(it)) }

        when {
            state.loading && state.services.isEmpty() ->
                repeat(CARD_PLACEHOLDERS) { SpaSkeleton(CARD_PLACEHOLDER_HEIGHT) }

            state.failed && state.services.isEmpty() -> MenuFailed { onEvent(TreatmentsEvent.Retry) }

            else -> Results(state, onEvent)
        }
    }
}

@Composable
private fun CategoryRail(chosen: ServiceCategory?, onChoose: (ServiceCategory?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpaFilterChip(
            label = stringResource(Res.string.treatments_filter_all),
            selected = chosen == null,
            onClick = { onChoose(null) },
        )
        ServiceCategory.entries.forEach { category ->
            SpaFilterChip(
                label = category.label,
                selected = chosen == category,
                onClick = { onChoose(category) },
            )
        }
    }
}

@Composable
private fun Results(state: TreatmentsState, onEvent: (TreatmentsEvent) -> Unit) {
    val shown = state.visible
    Text(
        text = stringResource(Res.string.treatments_count, shown.size),
        style = SpaTheme.textStyles.label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (shown.isEmpty()) {
        NoMatches(cleared = { onEvent(TreatmentsEvent.FiltersCleared) }, filtered = state.filtered)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        shown.forEach { service ->
            TreatmentCard(service) { onEvent(TreatmentsEvent.TreatmentChosen(service.id)) }
        }
    }
}

@Composable
private fun TreatmentCard(service: Service, onOpen: () -> Unit) {
    SpaTreatmentCard(
        title = service.name,
        priceLabel = stringResource(Res.string.booking_price, service.priceUsd),
        detail = stringResource(Res.string.service_minutes, service.minutes),
        onClick = onOpen,
        category = service.category.label,
        description = service.includes,
    )
}

@Composable
private fun NoMatches(cleared: () -> Unit, filtered: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(Res.string.treatments_empty_title),
            style = SpaTheme.textStyles.cardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.treatments_empty_body),
            style = SpaTheme.textStyles.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (filtered) {
            SpaSecondaryButton(
                text = stringResource(Res.string.treatments_empty_action),
                onClick = cleared,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun MenuFailed(onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(Res.string.booking_menu_failed),
                style = SpaTheme.textStyles.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            SpaSecondaryButton(text = stringResource(Res.string.booking_menu_retry), onClick = onRetry)
        }
    }
}
