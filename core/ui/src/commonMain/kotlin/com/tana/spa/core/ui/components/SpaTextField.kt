package com.tana.spa.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.spaPointer

/**
 * Material 3's outlined text field, coloured from the theme: the label floats into the outline,
 * the outline thickens to the accent on focus and turns to the error colour with an icon and a
 * message, and the supporting text sits under it.
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
    var hadFocus by remember { mutableStateOf(false) }
    val message = error ?: helper
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .spaPointer(enabled, PointerIcon.Text)
            .onFocusChanged { focusState ->
                if (hadFocus && !focusState.isFocused) onFocusLost?.invoke()
                hadFocus = focusState.isFocused
            }
            .then(if (error != null) Modifier.semantics { this.error(error) } else Modifier),
        enabled = enabled,
        textStyle = SpaTheme.textStyles.fieldText,
        label = { Text(label) },
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) } },
        trailingIcon = when {
            trailing != null -> trailing
            error != null -> ({ SpaIconImage(SpaIcon.Warning, size = 24.dp, tint = MaterialTheme.colorScheme.error) })
            else -> null
        },
        supportingText = message?.let { text ->
            {
                Text(
                    text,
                    modifier = if (error != null) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
                )
            }
        },
        isError = error != null,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = MaterialTheme.shapes.extraSmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            errorContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}
