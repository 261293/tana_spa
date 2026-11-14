package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.formatDayAndTime
import com.tana.spa.core.ui.spaClickable
import com.tana.spa.feature.booking.presentation.CurrentVisit
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.visit_in_progress
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun CurrentVisitStrip(onOpen: (String) -> Unit) {
    val current: CurrentVisit = koinInject()
    val snapshot by current.visit.collectAsStateWithLifecycle()
    val visit = snapshot ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .spaClickable { onOpen(visit.booking.id) }
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = visit.booking.serviceName,
                style = SpaTheme.textStyles.body,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (visit.underWay) {
                    stringResource(Res.string.visit_in_progress)
                } else {
                    formatDayAndTime(visit.booking.startDayEpochDay, visit.booking.startHour)
                },
                style = SpaTheme.textStyles.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SpaIconImage(SpaIcon.ChevronRight, size = 18.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
