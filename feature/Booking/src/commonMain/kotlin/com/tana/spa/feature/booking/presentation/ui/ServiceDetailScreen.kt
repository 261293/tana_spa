package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaBackLink
import com.tana.spa.core.ui.components.SpaEyebrow
import com.tana.spa.core.ui.components.SpaImageTile
import com.tana.spa.core.ui.components.SpaIncludeRow
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.feature.booking.presentation.ServiceDetailEffect
import com.tana.spa.feature.booking.presentation.ServiceDetailEvent
import com.tana.spa.feature.booking.presentation.ServiceDetailState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.booking_price
import com.tana.spa.feature.booking.resources.service_back
import com.tana.spa.feature.booking.resources.service_book
import com.tana.spa.feature.booking.resources.service_eyebrow
import com.tana.spa.feature.booking.resources.service_failed_title
import com.tana.spa.feature.booking.resources.service_includes
import com.tana.spa.feature.booking.resources.service_minutes
import com.tana.spa.feature.booking.resources.service_missing_body
import com.tana.spa.feature.booking.resources.service_missing_title
import com.tana.spa.feature.booking.resources.service_note
import com.tana.spa.feature.booking.resources.service_retry
import com.tana.spa.shared.spa.Service
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

private const val HERO_RATIO = 3f / 2f

/** Stateless: renders [state], reports intents through [onEvent], routes one-shot [effects]. */
@Composable
fun ServiceDetailScreen(
    state: ServiceDetailState,
    effects: Flow<ServiceDetailEffect>,
    onEvent: (ServiceDetailEvent) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                ServiceDetailEffect.ReturnToBooking -> onBack()
            }
        }
    }

    // Reading width: one column at every window size, centred and guttered by SpaPage.
    SpaPage(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        maxWidth = SpaPageWidth.Reading,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SpaBackLink(stringResource(Res.string.service_back), onBack)
        when {
            state.loading -> Loading()
            state.service != null -> Treatment(state.service, onBook = { onEvent(ServiceDetailEvent.Book) })
            state.failed -> LoadFailed(onRetry = { onEvent(ServiceDetailEvent.Retry) })
            else -> Missing()
        }
    }
}

@Composable
private fun Treatment(service: Service, onBook: () -> Unit) {
    SpaImageTile(
        modifier = Modifier.fillMaxWidth().aspectRatio(HERO_RATIO),
        shape = MaterialTheme.shapes.extraLarge,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SpaEyebrow(
            stringResource(
                Res.string.service_eyebrow,
                service.category.label,
                stringResource(Res.string.service_minutes, service.minutes),
            ),
        )
        Text(
            text = service.name,
            style = SpaTheme.textStyles.displayTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.booking_price, service.priceUsd),
            style = SpaTheme.textStyles.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = stringResource(Res.string.service_includes),
            style = SpaTheme.textStyles.screenTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Column { service.includes.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.forEach { SpaIncludeRow(it) } }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpaPrimaryButton(
            text = stringResource(Res.string.service_book, stringResource(Res.string.booking_price, service.priceUsd)),
            onClick = onBook,
        )
        Text(
            text = stringResource(Res.string.service_note, service.minutes),
            style = SpaTheme.textStyles.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val SEPARATOR = "·"

@Composable
private fun Missing() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.service_missing_title),
            style = SpaTheme.textStyles.cardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.service_missing_body),
            style = SpaTheme.textStyles.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.service_failed_title),
            style = SpaTheme.textStyles.cardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        SpaPrimaryButton(text = stringResource(Res.string.service_retry), onClick = onRetry)
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
