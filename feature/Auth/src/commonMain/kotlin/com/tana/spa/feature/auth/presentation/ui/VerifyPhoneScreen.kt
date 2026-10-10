package com.tana.spa.feature.auth.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaOtpField
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.feature.auth.presentation.manipulator.VerifyMessage
import com.tana.spa.feature.auth.presentation.manipulator.VerifyPhoneEffect
import com.tana.spa.feature.auth.presentation.manipulator.VerifyPhoneEvent
import com.tana.spa.feature.auth.presentation.manipulator.VerifyPhoneState
import com.tana.spa.feature.auth.resources.Res
import com.tana.spa.feature.auth.resources.ic_phone
import com.tana.spa.feature.auth.resources.verify_button
import com.tana.spa.feature.auth.resources.verify_code_label
import com.tana.spa.feature.auth.resources.verify_didnt_get
import com.tana.spa.feature.auth.resources.verify_expires_in_minutes
import com.tana.spa.feature.auth.resources.verify_resend
import com.tana.spa.feature.auth.resources.verify_resend_in
import com.tana.spa.feature.auth.resources.verify_sending
import com.tana.spa.feature.auth.resources.verify_skip
import com.tana.spa.feature.auth.resources.verify_subtitle
import com.tana.spa.feature.auth.resources.verify_subtitle_generic
import com.tana.spa.feature.auth.resources.verify_title
import com.tana.spa.shared.auth.AuthValidation
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Phone-verification screen. Not in the Figma file: laid out like the reference OTP screen
 * (icon badge, title, sent-to pill, grouped code cells, expiry note, confirm button, resend,
 * secondary link) in this app's own colours and type.
 */
@Composable
fun VerifyPhoneScreen(
    state: VerifyPhoneState,
    effects: Flow<VerifyPhoneEffect>,
    onEvent: (VerifyPhoneEvent) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                VerifyPhoneEffect.NavigateHome -> onNavigateHome()
                VerifyPhoneEffect.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        VerifyPhoneContent(state = state, onEvent = onEvent)
    }
}

@Composable
private fun VerifyPhoneContent(state: VerifyPhoneState, onEvent: (VerifyPhoneEvent) -> Unit) {
    val focusManager = LocalFocusManager.current
    val complete = state.code.length == AuthValidation.OTP_CODE_LENGTH
    val canResend = state.resendSecondsLeft == 0 && !state.isSending && !state.isVerifying

    Column(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 15.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_phone),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(30.dp),
            )
        }

        Text(
            text = stringResource(Res.string.verify_title),
            style = SpaTheme.textStyles.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp).semantics { heading() },
        )

        when {
            state.maskedPhone != null -> {
                Subtitle(stringResource(Res.string.verify_subtitle))
                // The destination is shown masked, in a pill: enough to spot a wrong number.
                Text(
                    text = state.maskedPhone,
                    style = SpaTheme.textStyles.caption,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            state.isSending -> Subtitle(stringResource(Res.string.verify_sending))

            else -> Subtitle(stringResource(Res.string.verify_subtitle_generic))
        }

        SpaOtpField(
            value = state.code,
            onValueChange = { onEvent(VerifyPhoneEvent.CodeChanged(it)) },
            label = stringResource(Res.string.verify_code_label),
            length = AuthValidation.OTP_CODE_LENGTH,
            isError = state.message is VerifyMessage.WrongCode || state.message is VerifyMessage.Failure,
            // Kept enabled while a code is being sent so the keyboard is already up when it arrives.
            enabled = !state.isVerifying,
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onEvent(VerifyPhoneEvent.Submit)
            }),
            modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
        )

        val message = state.message.text()
        if (message != null) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = SpaTheme.textStyles.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
            )
        } else if (state.expiresInMinutes != null) {
            Text(
                text = pluralStringResource(Res.plurals.verify_expires_in_minutes, state.expiresInMinutes, state.expiresInMinutes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = SpaTheme.textStyles.caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
            )
        }

        SpaPrimaryButton(
            text = stringResource(Res.string.verify_button),
            onClick = {
                focusManager.clearFocus()
                onEvent(VerifyPhoneEvent.Submit)
            },
            enabled = complete && !state.isSending,
            loading = state.isVerifying,
            modifier = Modifier.padding(top = 24.dp),
        )

        AuthLinkPrompt(
            prompt = stringResource(Res.string.verify_didnt_get),
            action = if (state.resendSecondsLeft > 0) {
                stringResource(Res.string.verify_resend_in, formatCountdown(state.resendSecondsLeft))
            } else {
                stringResource(Res.string.verify_resend)
            },
            enabled = canResend,
            onClick = { onEvent(VerifyPhoneEvent.Resend) },
            modifier = Modifier.padding(top = 12.dp),
        )

        Text(
            text = stringResource(Res.string.verify_skip),
            style = SpaTheme.textStyles.sectionLead,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable(enabled = !state.isVerifying, role = Role.Button) { onEvent(VerifyPhoneEvent.Skip) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Subtitle(text: String) {
    Text(
        text = text,
        style = SpaTheme.textStyles.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
    )
}

/** 42 -> `0:42`, 75 -> `1:15`. */
internal fun formatCountdown(totalSeconds: Int): String = "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
