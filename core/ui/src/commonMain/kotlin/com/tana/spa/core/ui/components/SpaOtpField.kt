package com.tana.spa.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.SpaColors

private val GroupShape = RoundedCornerShape(10.dp)
private val ActiveShape = RoundedCornerShape(8.dp)
private val CellWidth = 44.dp
private val CellHeight = 52.dp

/**
 * One-time-code input drawn as connected cells in groups (default `123 – 456`), backed by a
 * single invisible text field so the keyboard, paste, backspace, accessibility and platform
 * autofill all behave like a normal field.
 *
 * Only digits are accepted and the value is capped at [length]; [onValueChange] always receives
 * the cleaned value. Requests focus when first shown so the numeric keyboard is already up.
 *
 * @param label read by screen readers (the cells themselves are decorative).
 * @param groupSize cells per group; groups are separated by a dash. Use [length] for no grouping.
 */
@Composable
fun SpaOtpField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    length: Int = 6,
    groupSize: Int = 3,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    require(length > 0 && groupSize in 1..length) { "groupSize must be between 1 and length" }
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // The field may not be attached yet on the very first frame (notably on iOS).
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    val textStyle = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        color = SpaColors.TextPrimary,
        textAlign = TextAlign.Center,
    )
    val activeIndex = value.length.coerceAtMost(length - 1)
    val groups = (0 until length).chunked(groupSize)

    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(length)) },
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle.copy(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = keyboardActions,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label },
        decorationBox = { innerTextField ->
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    groups.forEachIndexed { groupIndex, indices ->
                        if (groupIndex > 0) {
                            Text("–", style = textStyle.copy(color = SpaColors.TextSecondary), modifier = Modifier.width(24.dp))
                        }
                        Row(
                            modifier = Modifier
                                .height(CellHeight)
                                .background(SpaColors.Surface, GroupShape)
                                .border(
                                    BorderStroke(1.dp, if (isError) SpaColors.Error else SpaColors.FieldBorder),
                                    GroupShape,
                                ),
                        ) {
                            indices.forEachIndexed { position, index ->
                                if (position > 0) {
                                    // thin divider between cells of one group
                                    Box(Modifier.width(1.dp).fillMaxHeight().background(SpaColors.ButtonBorder))
                                }
                                val active = focused && enabled && index == activeIndex
                                Box(
                                    modifier = Modifier
                                        .width(CellWidth)
                                        .fillMaxHeight()
                                        .then(
                                            if (active) {
                                                Modifier.border(
                                                    BorderStroke(2.dp, if (isError) SpaColors.Error else SpaColors.Primary),
                                                    ActiveShape,
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(text = value.getOrNull(index)?.toString().orEmpty(), style = textStyle)
                                }
                            }
                        }
                    }
                }
                // The real field sits invisibly on top: taps focus it, input flows through it,
                // but nothing draws (the cells above are the display).
                Box(Modifier.matchParentSize().alpha(0f)) { innerTextField() }
            }
        },
    )
}
