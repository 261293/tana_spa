package com.tana.spa.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.spaPointer

private val FieldHeight = 56.dp
private const val DISABLED_ALPHA = 0.38f
private const val DISABLED_OUTLINE_ALPHA = 0.12f

/**
 * An outlined web-form input: the label sits above the box, the box is the page's own pale fill
 * with a hairline outline, and the outline thickens to the accent on focus and to red on an error.
 *
 * This is the one place the app does **not** use the Material component. Material's `TextField`
 * crashes at runtime with the versions this project pins — `material3 1.11.0-alpha07` builds a
 * `CustomStyle` that `compose 1.12.1`'s foundation does not implement, so composing one throws
 * `AbstractMethodError: … TextFieldDefaults$$Lambda … applyStyle(CustomStyleScope)`. A previous
 * material3 bump broke the web target and was reverted, so the field is built on `BasicTextField`
 * instead; swap it back when the two artifacts line up again. See `docs/material-design.md`.
 *
 * @param helper supporting text shown under the field; [error] replaces it and turns it red.
 */
@Composable
fun SpaTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: Painter? = null,
    helper: String? = null,
    error: String? = null,
    onFocusLost: (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    var hadFocus by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val fieldShape = MaterialTheme.shapes.extraSmall
    val inkAlpha = if (enabled) 1f else DISABLED_ALPHA
    val textStyle = SpaTheme.textStyles.fieldText.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = inkAlpha))
    val outline = when {
        error != null -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_OUTLINE_ALPHA)
        hovered -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.outline
    }
    val outlineWidth = if (focused) 2.dp else 1.dp

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = SpaTheme.textStyles.caption.copy(fontWeight = FontWeight.Bold),
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                error != null -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .spaPointer(enabled, PointerIcon.Text)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                ) { fieldFocus.requestFocus() },
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .hoverable(interactionSource, enabled = enabled)
                .spaPointer(enabled, PointerIcon.Text)
                .onFocusChanged { focusState ->
                    if (hadFocus && !focusState.isFocused) onFocusLost?.invoke()
                    hadFocus = focusState.isFocused
                }
                .background(MaterialTheme.colorScheme.background, fieldShape)
                .border(BorderStroke(outlineWidth, outline), fieldShape)
                .semantics {
                    contentDescription = label
                    if (error != null) this.error(error)
                },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = FieldHeight)
                        .padding(
                            start = if (leadingIcon !=
                                null
                            ) {
                                12.dp
                            } else {
                                16.dp
                            },
                            end = if (trailing != null || error != null) 4.dp else 16.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Image(
                            painter = leadingIcon,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = inkAlpha)),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = inkAlpha)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(4.dp))
                        trailing()
                    } else if (error != null) {
                        SpaIconImage(
                            SpaIcon.Warning,
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            },
        )
        val message = error ?: helper
        if (message != null) {
            Text(
                text = message,
                style = SpaTheme.textStyles.fieldError.copy(
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .then(if (error != null) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
            )
        }
    }
}
