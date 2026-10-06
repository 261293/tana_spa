package com.tana.spa.feature.auth.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.SpaColors

/**
 * "Bạn chưa có tài khoản? **Đăng kí ngay**": plain [prompt] followed by a bold pink [action].
 * The whole line is the tap target, because a 16sp inline span alone is too small to hit on a phone.
 * When [enabled] is false the action turns grey (e.g. a resend countdown).
 */
@Composable
internal fun AuthLinkPrompt(
    prompt: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = buildAnnotatedString {
            append(prompt)
            append(" ")
            withStyle(SpanStyle(color = if (enabled) SpaColors.Link else SpaColors.TextSecondary, fontWeight = FontWeight.Bold)) {
                append(action)
            }
        },
        fontSize = 16.sp,
        color = SpaColors.TextPrimary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
    )
}
