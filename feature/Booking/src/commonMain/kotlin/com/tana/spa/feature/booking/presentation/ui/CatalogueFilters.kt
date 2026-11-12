package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaFilterChip
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaTextField
import com.tana.spa.core.ui.localizedLabel
import com.tana.spa.feature.booking.presentation.BookingEvent
import com.tana.spa.feature.booking.presentation.BookingState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.treatments_empty_action
import com.tana.spa.feature.booking.resources.treatments_empty_body
import com.tana.spa.feature.booking.resources.treatments_empty_title
import com.tana.spa.feature.booking.resources.treatments_filter_all
import com.tana.spa.feature.booking.resources.treatments_search_label
import com.tana.spa.feature.booking.resources.treatments_search_placeholder
import com.tana.spa.shared.spa.ServiceCategory
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CatalogueFilters(state: BookingState, onEvent: (BookingEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SpaTextField(
            label = stringResource(Res.string.treatments_search_label),
            value = state.query,
            onValueChange = { onEvent(BookingEvent.QueryChanged(it)) },
            placeholder = stringResource(Res.string.treatments_search_placeholder),
        )
        CategoryRail(state.category) { onEvent(BookingEvent.CategoryChosen(it)) }
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
                label = category.localizedLabel(),
                selected = chosen == category,
                onClick = { onChoose(category) },
            )
        }
    }
}

@Composable
internal fun NoMatches(onClear: () -> Unit) {
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
        SpaSecondaryButton(
            text = stringResource(Res.string.treatments_empty_action),
            onClick = onClear,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
