package com.tana.spa.feature.auth.presentation.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.spaPointer

/**
 * "Bạn chưa có tài khoản? **Đăng kí ngay**": plain [prompt] followed by a bold pink [action].
 * The whole line is one text button, because a 16sp inline span alone is too small to hit on a phone.
 * When [enabled] is false the action turns grey (e.g. a resend countdown).
 */
@Composable
internal fun AuthLinkPrompt(prompt: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = modifier.spaPointer(enabled),
    ) {
        Text(
            text = buildAnnotatedString {
                append(prompt)
                append(" ")
                withStyle(
                    SpanStyle(
                        color = if (enabled) SpaTheme.extraColors.link else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append(action)
                }
            },
            style = SpaTheme.textStyles.prompt,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
