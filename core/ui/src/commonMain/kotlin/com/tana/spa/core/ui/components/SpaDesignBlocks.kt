package com.tana.spa.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme

private const val MEDIA_RATIO = 4f / 3f
private const val DESCRIPTION_LINES = 2

/** The uppercase tracked label the design sets above every heading. */
@Composable
fun SpaEyebrow(text: String, modifier: Modifier = Modifier, color: Color = SpaTheme.extraColors.eyebrow) {
    Text(text = text.uppercase(), style = SpaTheme.textStyles.overline, color = color, modifier = modifier)
}

/** Eyebrow, serif heading and an optional note or trailing action, as the design opens a block. */
@Composable
fun SpaSectionHead(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SpaEyebrow(eyebrow)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = SpaTheme.textStyles.screenTitle,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            action?.invoke()
        }
        note?.let {
            Text(text = it, style = SpaTheme.textStyles.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The design's treatment card: a 4:3 picture, then category, name and description, closed by a
 * hairline foot carrying the price and the length of the treatment.
 */
@Composable
fun SpaTreatmentCard(
    title: String,
    priceLabel: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    category: String? = null,
    description: String? = null,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        SpaImageTile(
            modifier = Modifier.fillMaxWidth().aspectRatio(MEDIA_RATIO),
            shape = RoundedCornerShape(0.dp),
        )
        Column(
            Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            category?.let { SpaEyebrow(it) }
            Text(text = title, style = SpaTheme.textStyles.cardTitle, color = MaterialTheme.colorScheme.onSurface)
            description?.let {
                Text(
                    text = it,
                    style = SpaTheme.textStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = DESCRIPTION_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = priceLabel, style = SpaTheme.textStyles.cardTitle, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = detail,
                    style = SpaTheme.textStyles.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SpaIconImage(SpaIcon.ArrowRight, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The hairline-topped row of label/value pairs under the hero ("Open · Team · Rituals"). */
@Composable
fun SpaFacts(facts: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            facts.forEach { (label, value) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SpaEyebrow(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = value,
                        style = SpaTheme.textStyles.body,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The dark forest panel the design closes a page with, holding one light action. */
@Composable
fun SpaCtaPanel(eyebrow: String, title: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().spaPocket(MaterialTheme.shapes.extraLarge).padding(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SpaEyebrow(eyebrow, color = SpaTheme.extraColors.onPocketMuted)
            Text(text = title, style = SpaTheme.textStyles.screenTitle, color = SpaTheme.extraColors.onPocket)
            SpaPocketButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** The quiet "back to the list" control the design puts above a detail page. */
@Composable
fun SpaBackLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpaIconImage(SpaIcon.ChevronLeft, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = text, style = SpaTheme.textStyles.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The sage date block on the left of a booking card: weekday, date, month. */
@Composable
fun SpaDateBlock(weekday: String, day: String, month: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpaEyebrow(weekday, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = day, style = SpaTheme.textStyles.cardTitle, color = MaterialTheme.colorScheme.onSurface)
        Text(text = month, style = SpaTheme.textStyles.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One line of "what's included": the design's clay dash above a hairline. */
@Composable
fun SpaIncludeRow(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "—", style = SpaTheme.textStyles.caption, color = MaterialTheme.colorScheme.tertiary)
            Text(text = text, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** An outlined pill naming a person, as the design lists a treatment's therapists. */
@Composable
fun SpaPersonPill(name: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
            .padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text = name, style = SpaTheme.textStyles.caption, color = MaterialTheme.colorScheme.onSurface)
    }
}
