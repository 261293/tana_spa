package com.tana.spa.core.ui.components

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tana.spa.core.ui.SpaTheme

private const val DISABLED_ALPHA = 0.5f

/**
 * Material's filter chip in this design's tones: a selected chip is a celadon container with moss
 * text, and no chip is outlined — the palette separates by tone, not by line. Material keeps the
 * 48dp touch target, so the chips stay tappable at their 32dp visual height.
 *
 * Used for every "pick one of these" row: days, times, code states, eligible treatments.
 */
@Composable
fun SpaFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: SpaIcon? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = SpaTheme.textStyles.body) },
        leadingIcon = leadingIcon?.let {
            {
                SpaIconImage(
                    icon = it,
                    size = FilterChipDefaults.IconSize,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        shape = MaterialTheme.shapes.extraSmall,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier,
    )
}
