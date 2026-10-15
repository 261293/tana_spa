package com.tana.spa.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme

private val ButtonShape = RoundedCornerShape(10.dp)
private val ButtonHeight = 52.dp
private const val DISABLED_ALPHA = 0.5f
private const val POCKET_DISABLED_ALPHA = 0.25f

/** Full-width moss call-to-action. While [loading] it shows a spinner and ignores taps. */
@Composable
fun SpaPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text, style = SpaTheme.textStyles.buttonLabel)
        }
    }
}

/**
 * The action inside a dark [spaPocket]: light moss on deep moss, with the trailing arrow the
 * design puts on the booking request.
 */
@Composable
fun SpaPocketButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: SpaIcon? = SpaIcon.ArrowRight,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = SpaTheme.extraColors.pocketAction,
            contentColor = SpaTheme.extraColors.onPocketAction,
            disabledContainerColor = SpaTheme.extraColors.pocketAction.copy(alpha = POCKET_DISABLED_ALPHA),
            disabledContentColor = SpaTheme.extraColors.onPocket.copy(alpha = DISABLED_ALPHA),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = text, style = SpaTheme.textStyles.buttonLabel)
            icon?.let { SpaIconImage(it, size = 18.dp) }
        }
    }
}

/** Full-width quiet button with a leading brand image (e.g. "Continue with Google"). */
@Composable
fun SpaSocialButton(text: String, icon: Painter, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
    ) {
        Image(painter = icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(10.dp))
        Text(text = text, style = SpaTheme.textStyles.buttonLabel)
    }
}

/**
 * Full-width low-emphasis action (Material's filled tonal button), for the dismiss half of a
 * two-button choice. [contentColor] carries a destructive action, e.g. signing out.
 */
@Composable
fun SpaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
        ),
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
    ) {
        Text(text = text, style = SpaTheme.textStyles.buttonLabel)
    }
}
