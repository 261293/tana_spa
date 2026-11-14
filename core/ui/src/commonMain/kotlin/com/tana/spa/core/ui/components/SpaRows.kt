package com.tana.spa.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.spaPointer

enum class SpaTone { Neutral, Selected, Positive, Pending, Negative }

@Composable
fun SpaTonePill(text: String, tone: SpaTone, modifier: Modifier = Modifier, icon: SpaIcon? = null) {
    val (container, content) = toneColors(tone)
    SpaStatusPill(text = text, container = container, content = content, modifier = modifier, icon = icon)
}

@Composable
private fun toneColors(tone: SpaTone): Pair<Color, Color> = when (tone) {
    SpaTone.Neutral -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
    SpaTone.Selected -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    SpaTone.Positive -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    SpaTone.Pending -> SpaTheme.extraColors.warningContainer to SpaTheme.extraColors.warning
    SpaTone.Negative -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}

@Composable
fun SpaRowGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    rows: List<@Composable () -> Unit>,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null || action != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                title?.let {
                    Text(
                        text = it,
                        style = SpaTheme.textStyles.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                }
                action?.invoke()
            }
        }
        val shape = MaterialTheme.shapes.medium
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface, shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                row()
            }
        }
    }
}

@Composable
fun SpaListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    overline: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: (() -> Unit)? = null,
) {
    val interactive = onClick != null
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = SpaTheme.textStyles.body,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        overlineContent = overline?.let { { Text(it, style = SpaTheme.textStyles.label) } },
        supportingContent = supporting?.let {
            { Text(it, style = SpaTheme.textStyles.caption, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = leading,
        trailingContent = trailing ?: if (interactive && !selected) {
            { SpaIconImage(SpaIcon.ChevronRight, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (selected) {
            { SpaIconImage(SpaIcon.Check, size = 18.dp, tint = MaterialTheme.colorScheme.secondary) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            overlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.spaPointer(enabled).selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
                },
            ),
    )
}

@Composable
fun SpaPrice(text: String, modifier: Modifier = Modifier, emphasis: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = if (emphasis) SpaTheme.textStyles.screenTitle else SpaTheme.textStyles.cardTitle,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
fun SpaFactGrid(facts: List<Pair<String, String>>, modifier: Modifier = Modifier, columns: Int = 2) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        facts.chunked(columns).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                line.forEach { (label, value) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = SpaTheme.textStyles.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                repeat(columns - line.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    activeBorderColor = MaterialTheme.colorScheme.outline,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline,
                ),
                icon = {},
                modifier = Modifier.spaPointer(),
                label = { Text(label, style = SpaTheme.textStyles.caption, fontWeight = FontWeight.Medium, maxLines = 1) },
            )
        }
    }
}

@Composable
fun SpaUnreadDot(modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
}

@Composable
fun SpaEmptyBlock(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .spaDashedBorder(MaterialTheme.colorScheme.outlineVariant, shape, 1.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
