package com.tana.spa.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme

private val StepNumberSize = 26.dp
private val DateCellWidth = 72.dp
private val SlotHeight = 46.dp
private val ChoiceShape = RoundedCornerShape(14.dp)
private const val DISABLED_ALPHA = 0.55f
private const val STEP_LABEL_LINES = 2

/**
 * The numbered trail across the top of the booking flow. Each step is underlined; the one in hand
 * carries a filled circle and a forest rule, and the ones behind it carry a check.
 */
@Composable
fun SpaStepTrail(steps: List<String>, current: Int, modifier: Modifier = Modifier) {
    // Intrinsic height keeps every rule on one line when a label wraps to two.
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, label ->
            val done = index < current
            val isCurrent = index == current
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().weight(1f).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepNumber(index + 1, done, isCurrent)
                    Text(
                        text = label,
                        style = SpaTheme.textStyles.caption,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.onBackground
                            done -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = STEP_LABEL_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider(
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun StepNumber(number: Int, done: Boolean, current: Boolean) {
    val container = when {
        current -> MaterialTheme.colorScheme.primary
        done -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val border = when {
        current -> MaterialTheme.colorScheme.primary
        done -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    val content = when {
        current -> MaterialTheme.colorScheme.onPrimary
        done -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier.size(StepNumberSize).background(container, CircleShape).border(1.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            SpaIconImage(SpaIcon.Check, size = 14.dp, tint = content)
        } else {
            Text(number.toString(), style = SpaTheme.textStyles.label, fontWeight = FontWeight.SemiBold, color = content)
        }
    }
}

/** The design's panel: a white sheet inside a hairline, opened by a serif title. */
@Composable
fun SpaPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(24.dp),
    ) {
        if (title != null || action != null) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = SpaTheme.textStyles.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                }
                action?.invoke()
            }
        }
        content()
    }
}

/** One day on the booking rail: weekday, date, month, filled with forest once it is chosen. */
@Composable
fun SpaDateCell(
    weekday: String,
    day: String,
    month: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val label = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val muted = if (selected) SpaTheme.extraColors.onPocketMuted else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .widthIn(min = DateCellWidth)
            .background(container, shape)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .alphaIfDisabled(enabled)
            .describedAs(contentDescription),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(weekday.uppercase(), style = SpaTheme.textStyles.overline, color = muted, textAlign = TextAlign.Center)
        Text(day, style = SpaTheme.textStyles.cardTitle, color = label)
        Text(month, style = SpaTheme.textStyles.label, color = muted, textAlign = TextAlign.Center)
    }
}

/** One open time. The grid of these is the design's slot block. */
@Composable
fun SpaSlotButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = SlotHeight)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .alphaIfDisabled(enabled),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = SpaTheme.textStyles.caption,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** A row in a "pick one" list: the therapist choices, and the first-available option above them. */
@Composable
fun SpaChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                ChoiceShape,
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ChoiceShape,
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp)
            .alphaIfDisabled(enabled),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = SpaTheme.textStyles.body,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = SpaTheme.textStyles.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            SpaIconImage(SpaIcon.Check, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A hairline-topped line of the reservation summary. [total] is the design's heavier last row. */
@Composable
fun SpaSummaryRow(label: String, value: String, modifier: Modifier = Modifier, total: Boolean = false) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(top = if (total) 14.dp else 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = SpaTheme.textStyles.label,
                fontWeight = if (total) FontWeight.SemiBold else FontWeight.Normal,
                color = if (total) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = if (total) SpaTheme.textStyles.cardTitle else SpaTheme.textStyles.caption,
                fontWeight = if (total) FontWeight.Medium else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun Modifier.alphaIfDisabled(enabled: Boolean): Modifier = if (enabled) this else alpha(DISABLED_ALPHA)

private fun Modifier.describedAs(description: String?): Modifier =
    if (description == null) this else clearAndSetSemantics { contentDescription = description }
