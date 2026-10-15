package com.tana.spa.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.SpaPebbleShape
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.resources.Res
import com.tana.spa.core.ui.resources.bg_topo
import com.tana.spa.core.ui.resources.brand_name
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The three signature surfaces of the design file. CSS inset shadows have no Compose equivalent,
 * so the recess and the bevel are drawn as short gradients over the fill.
 */
private const val RECESS_ALPHA = 0.10f
private const val BEVEL_ALPHA = 0.75f

/** Recessed linen tray: time slots, inline edit forms, the code checker. */
@Composable
fun Modifier.spaFelt(shape: Shape = MaterialTheme.shapes.medium): Modifier {
    val recess = MaterialTheme.colorScheme.onSurface.copy(alpha = RECESS_ALPHA)
    val bevel = Color.White.copy(alpha = BEVEL_ALPHA)
    return background(SpaTheme.extraColors.felt, shape)
        .clip(shape)
        .drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(listOf(recess, Color.Transparent), endY = 10.dp.toPx()))
            drawRect(bevel, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = size.copy(height = 1.dp.toPx()))
        }
}

/** The single darkened moss pocket per screen, holding that screen's primary action. */
@Composable
fun Modifier.spaPocket(shape: Shape = MaterialTheme.shapes.large): Modifier {
    val glow = SpaTheme.extraColors.pocketAction.copy(alpha = 0.16f)
    return background(SpaTheme.extraColors.pocket, shape)
        .clip(shape)
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.radialGradient(
                    listOf(glow, Color.Transparent),
                    center = Offset(size.width / 2f, 0f),
                    radius = size.width * 0.8f,
                ),
            )
        }
}

/**
 * The contour-map texture from the design file, anchored (never tiled) behind a panel. It is
 * decorative; [alignment] places the 480x360 plate the way the screen wants it.
 */
@Composable
fun Modifier.spaTopo(alignment: Alignment = Alignment.BottomEnd): Modifier =
    paint(painterResource(Res.drawable.bg_topo), alignment = alignment, contentScale = ContentScale.None)

/** Cork-pebble bevel: the raised chips, slots and quiet buttons. */
@Composable
fun Modifier.spaCork(shape: Shape = MaterialTheme.shapes.extraSmall, color: Color = MaterialTheme.colorScheme.surfaceVariant): Modifier {
    val bevel = Color.White.copy(alpha = BEVEL_ALPHA)
    return background(color, shape)
        .clip(shape)
        .drawWithContent {
            drawContent()
            drawRect(bevel, size = size.copy(height = 1.dp.toPx()))
        }
}

/** Dashed outline: the frame around a discount code, and a proposed slot on the staff schedule. */
fun Modifier.spaDashedBorder(color: Color, shape: Shape, width: Dp = 1.5.dp): Modifier = drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rounded -> drawPath(Path().apply { addRoundRect(outline.roundRect) }, color, style = stroke)
        is Outline.Generic -> drawPath(outline.path, color, style = stroke)
        is Outline.Rectangle -> drawRect(color, style = stroke)
    }
}

/**
 * Material's card, flat: this palette layers by tone, so the card carries no elevation of its own.
 * [onClick] makes it one of Material's clickable cards, which is how a treatment or a therapist is
 * chosen.
 */
@Composable
fun SpaCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val container = when {
        selected -> MaterialTheme.colorScheme.primaryContainer

        // The design tints a row on hover instead of raising it; a transparent card has nothing
        // to raise, and Material's hovered elevation would draw a shadow around empty space.
        hovered && onClick != null -> MaterialTheme.colorScheme.surfaceVariant

        else -> color
    }
    val colors = CardDefaults.cardColors(
        containerColor = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    // Flat in every state: this palette layers by tone, and shadows are not part of it.
    val elevation = CardDefaults.cardElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
        draggedElevation = 0.dp,
        disabledElevation = 0.dp,
    )
    if (onClick == null) {
        Card(shape = shape, colors = colors, elevation = elevation, modifier = modifier) {
            Box(Modifier.padding(padding)) { content() }
        }
    } else {
        Card(
            onClick = onClick,
            shape = shape,
            colors = colors,
            elevation = elevation,
            interactionSource = interactionSource,
            modifier = modifier,
        ) {
            Box(Modifier.padding(padding)) { content() }
        }
    }
}

/**
 * The display title that opens every screen. [accent] is the moss-coloured tail of the headline
 * ("What does your body *need today?*"), which the design sets in italic serif; this build has one
 * sans family, so the accent carries colour and weight instead.
 */
@Composable
fun SpaDisplayTitle(lead: String, accent: String? = null, modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        append(lead)
        if (accent != null) {
            append(" ")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) {
                append(accent)
            }
        }
    }
    Text(
        text = text,
        style = SpaTheme.textStyles.displayTitle,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.semantics { heading() },
    )
}

/** Display title plus the line of explanation under it. */
@Composable
fun SpaScreenHeader(title: String, accent: String? = null, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SpaDisplayTitle(title, accent)
        subtitle?.let {
            Text(text = it, style = SpaTheme.textStyles.bodyRelaxed, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Quiet group label above a block of rows ("Account", "Your bookings"). */
@Composable
fun SpaGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SpaTheme.textStyles.sectionLead.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/** Small state tag: "Awaiting confirmation", "Live", "Overlap". */
@Composable
fun SpaStatusPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: SpaIcon? = null) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = container, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { SpaIconImage(it, size = 13.dp, tint = content) }
            Text(text = text, style = SpaTheme.textStyles.label, color = content)
        }
    }
}

@Composable
fun SpaAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(shape = CircleShape, color = container, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = initials, style = SpaTheme.textStyles.label.copy(fontWeight = FontWeight.Bold), color = content)
        }
    }
}

/**
 * Stands in for the forest photography in the design file. There is no image loader on the
 * classpath yet, so the layout reserves the real crop and fills it tonally.
 */
@Composable
fun SpaImageTile(modifier: Modifier = Modifier, shape: Shape = SpaPebbleShape, icon: SpaIcon = SpaIcon.Leaf) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ),
            shape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        SpaIconImage(icon, size = 28.dp, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SpaBrand(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(34.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SpaIconImage(SpaIcon.Leaf, size = 19.dp, tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (!compact) {
            Text(
                text = stringResource(Res.string.brand_name),
                style = SpaTheme.textStyles.cardTitle,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** Dashed frame around a discount code, as on the offers card. */
@Composable
fun SpaCodeChip(code: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.spaDashedBorder(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = code,
                style = SpaTheme.textStyles.cardTitle.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
    }
}
