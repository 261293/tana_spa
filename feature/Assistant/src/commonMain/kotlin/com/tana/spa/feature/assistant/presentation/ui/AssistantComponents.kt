package com.tana.spa.feature.assistant.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaColors
import com.tana.spa.feature.assistant.resources.Res
import com.tana.spa.feature.assistant.resources.ic_sparkle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Window width from which the content is shown as a floating card instead of full-bleed. */
private val WideBreakpoint = 640.dp

/** The assistant's picture: a pink gradient disc with a sparkle. Replace with brand art when available. */
@Composable
internal fun AssistantAvatar(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(SpaColors.Primary, SpaColors.Link))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_sparkle),
            contentDescription = null,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/**
 * Page background plus a content column: on phones the content fills the screen, on wide
 * windows (web, tablets) it floats as a rounded card of at most [maxWidth], as in the reference.
 * Insets (status bar, notch, keyboard) are always respected.
 */
@Composable
internal fun ScreenCard(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 560.dp,
    cardColor: Color = SpaColors.Surface,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SpaColors.Background, Color(0xFFFFF1F5))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        val wide = this.maxWidth >= WideBreakpoint
        val cardModifier = if (wide) {
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxSize()
                .padding(vertical = 24.dp)
                .shadow(16.dp, RoundedCornerShape(24.dp), clip = false, ambientColor = Color(0x22000000))
                .clip(RoundedCornerShape(24.dp))
                .background(cardColor)
        } else {
            Modifier.fillMaxSize().background(cardColor)
        }
        Box(cardModifier) { content() }
    }
}

/** 40dp outlined button used for back/close, matching the reference's rounded-square icon button. */
@Composable
internal fun IconSquareButton(
    icon: DrawableResource,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mirrorInRtl: Boolean = false,
    round: Boolean = false,
) {
    val shape = if (round) CircleShape else RoundedCornerShape(10.dp)
    val mirror = mirrorInRtl && LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(shape)
            .background(SpaColors.Surface)
            .border(1.dp, SpaColors.FieldBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(SpaColors.TextPrimary),
            modifier = Modifier.size(20.dp).graphicsLayer { if (mirror) scaleX = -1f },
        )
    }
}

/** A checkbox with its label; the whole row is the tap target and a single accessibility node. */
@Composable
internal fun ConsentCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            // The row owns the click (bigger target, one accessibility node); the box only displays.
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = SpaColors.Primary,
                uncheckedColor = SpaColors.FieldBorder,
                checkmarkColor = Color.White,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) { label() }
    }
}
