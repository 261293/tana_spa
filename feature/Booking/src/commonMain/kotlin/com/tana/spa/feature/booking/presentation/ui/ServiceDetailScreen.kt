package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaImageTile
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.feature.booking.presentation.ServiceDetailEffect
import com.tana.spa.feature.booking.presentation.ServiceDetailEvent
import com.tana.spa.feature.booking.presentation.ServiceDetailState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.booking_price
import com.tana.spa.feature.booking.resources.service_back
import com.tana.spa.feature.booking.resources.service_book
import com.tana.spa.feature.booking.resources.service_duration
import com.tana.spa.feature.booking.resources.service_failed_title
import com.tana.spa.feature.booking.resources.service_includes
import com.tana.spa.feature.booking.resources.service_minutes
import com.tana.spa.feature.booking.resources.service_missing_body
import com.tana.spa.feature.booking.resources.service_missing_title
import com.tana.spa.feature.booking.resources.service_price
import com.tana.spa.feature.booking.resources.service_retry
import com.tana.spa.shared.spa.Service
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

private val HeroHeight = 180.dp

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
        BackRow(onBack)
        when {
            state.loading -> Loading()
            state.service != null -> Treatment(state.service, onBook = { onEvent(ServiceDetailEvent.Book) })
            state.failed -> LoadFailed(onRetry = { onEvent(ServiceDetailEvent.Retry) })
            else -> Missing()
        }
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    TextButton(onClick = onBack) {
        SpaIconImage(SpaIcon.ChevronLeft, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(Res.string.service_back), style = SpaTheme.textStyles.buttonLabel)
    }
}

@Composable
private fun Treatment(service: Service, onBook: () -> Unit) {
    SpaImageTile(Modifier.fillMaxWidth().height(HeroHeight))

    SpaScreenHeader(title = service.name, subtitle = service.category.label)

    SpaCard(padding = PaddingValues(vertical = 4.dp)) {
        Column {
            Fact(stringResource(Res.string.service_duration), stringResource(Res.string.service_minutes, service.minutes))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Fact(stringResource(Res.string.service_price), stringResource(Res.string.booking_price, service.priceUsd))
        }
    }

    SpaCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.service_includes),
                style = SpaTheme.textStyles.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpaIconImage(SpaIcon.Leaf, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
                Text(service.includes, style = SpaTheme.textStyles.bodyRelaxed, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    SpaPrimaryButton(text = stringResource(Res.string.service_book), onClick = onBook)
}

@Composable
private fun Fact(label: String, value: String) {
    ListItem(
        headlineContent = {
            Text(label, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Text(value, style = SpaTheme.textStyles.cardTitle, color = MaterialTheme.colorScheme.onBackground)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun Missing() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.service_missing_title),
            style = SpaTheme.textStyles.sectionLead,
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
            style = SpaTheme.textStyles.sectionLead,
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
