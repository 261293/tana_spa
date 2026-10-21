package com.tana.spa.feature.auth.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaSocialButton
import com.tana.spa.core.ui.components.SpaTextField
import com.tana.spa.feature.auth.presentation.manipulator.SignUpEffect
import com.tana.spa.feature.auth.presentation.manipulator.SignUpEvent
import com.tana.spa.feature.auth.presentation.manipulator.SignUpState
import com.tana.spa.feature.auth.resources.Res
import com.tana.spa.feature.auth.resources.email_label
import com.tana.spa.feature.auth.resources.email_placeholder
import com.tana.spa.feature.auth.resources.have_account
import com.tana.spa.feature.auth.resources.hide_password
import com.tana.spa.feature.auth.resources.ic_email
import com.tana.spa.feature.auth.resources.ic_eye
import com.tana.spa.feature.auth.resources.ic_eye_off
import com.tana.spa.feature.auth.resources.ic_google
import com.tana.spa.feature.auth.resources.ic_lock
import com.tana.spa.feature.auth.resources.ic_phone
import com.tana.spa.feature.auth.resources.ic_user
import com.tana.spa.feature.auth.resources.login_now
import com.tana.spa.feature.auth.resources.name_label
import com.tana.spa.feature.auth.resources.name_placeholder
import com.tana.spa.feature.auth.resources.password_label
import com.tana.spa.feature.auth.resources.password_placeholder
import com.tana.spa.feature.auth.resources.phone_label
import com.tana.spa.feature.auth.resources.phone_placeholder
import com.tana.spa.feature.auth.resources.show_password
import com.tana.spa.feature.auth.resources.signup_button
import com.tana.spa.feature.auth.resources.signup_intro
import com.tana.spa.feature.auth.resources.signup_title
import com.tana.spa.feature.auth.resources.signup_title_accent
import com.tana.spa.feature.auth.resources.signup_with_google
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Figma "SIGNUP" screen. Stateless: state comes in, [onEvent] goes out, effects are one-shot. */
@Composable
fun SignUpScreen(
    state: SignUpState,
    effects: Flow<SignUpEffect>,
    onEvent: (SignUpEvent) -> Unit,
    onNavigateToVerifyPhone: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                SignUpEffect.NavigateToVerifyPhone -> onNavigateToVerifyPhone()

                SignUpEffect.NavigateHome -> onNavigateHome()

                SignUpEffect.NavigateToLogin -> onNavigateToLogin()

                is SignUpEffect.ShowComingSoon -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(getString(effect.feature.res()))
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuthShell(
            title = stringResource(Res.string.signup_title),
            accent = stringResource(Res.string.signup_title_accent),
            intro = stringResource(Res.string.signup_intro),
        ) {
            SignUpForm(state = state, onEvent = onEvent)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) { data -> Snackbar(data) }
    }
}

@Composable
private fun SignUpForm(state: SignUpState, onEvent: (SignUpEvent) -> Unit) {
    val focusManager = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpaTextField(
                label = stringResource(Res.string.name_label),
                value = state.name,
                onValueChange = { onEvent(SignUpEvent.NameChanged(it)) },
                placeholder = stringResource(Res.string.name_placeholder),
                leadingIcon = painterResource(Res.drawable.ic_user),
                error = state.nameError.text(),
                onFocusLost = { onEvent(SignUpEvent.NameBlurred) },
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = next,
            )
            SpaTextField(
                label = stringResource(Res.string.email_label),
                value = state.email,
                onValueChange = { onEvent(SignUpEvent.EmailChanged(it)) },
                placeholder = stringResource(Res.string.email_placeholder),
                leadingIcon = painterResource(Res.drawable.ic_email),
                error = state.emailError.text(),
                onFocusLost = { onEvent(SignUpEvent.EmailBlurred) },
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = next,
            )
            SpaTextField(
                label = stringResource(Res.string.phone_label),
                value = state.phone,
                onValueChange = { onEvent(SignUpEvent.PhoneChanged(it)) },
                placeholder = stringResource(Res.string.phone_placeholder),
                leadingIcon = painterResource(Res.drawable.ic_phone),
                error = state.phoneError.text(),
                onFocusLost = { onEvent(SignUpEvent.PhoneBlurred) },
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                keyboardActions = next,
            )

            val showPassword = state.passwordVisible
            SpaTextField(
                label = stringResource(Res.string.password_label),
                value = state.password,
                onValueChange = { onEvent(SignUpEvent.PasswordChanged(it)) },
                placeholder = stringResource(Res.string.password_placeholder),
                leadingIcon = painterResource(Res.drawable.ic_lock),
                error = state.passwordError.text(),
                onFocusLost = { onEvent(SignUpEvent.PasswordBlurred) },
                enabled = !state.isLoading,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onEvent(SignUpEvent.Submit)
                }),
                trailing = {
                    val description =
                        stringResource(if (showPassword) Res.string.hide_password else Res.string.show_password)
                    Image(
                        painter = painterResource(if (showPassword) Res.drawable.ic_eye else Res.drawable.ic_eye_off),
                        contentDescription = description,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(role = Role.Button) { onEvent(SignUpEvent.TogglePasswordVisibility) },
                    )
                },
            )
        }

        state.failure.text()?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = SpaTheme.textStyles.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            )
        }

        SpaPrimaryButton(
            text = stringResource(Res.string.signup_button),
            onClick = {
                focusManager.clearFocus()
                onEvent(SignUpEvent.Submit)
            },
            loading = state.isLoading,
            modifier = Modifier.padding(top = 26.dp),
        )

        SpaSocialButton(
            text = stringResource(Res.string.signup_with_google),
            icon = painterResource(Res.drawable.ic_google),
            onClick = { onEvent(SignUpEvent.GoogleClicked) },
            enabled = !state.isLoading,
            modifier = Modifier.padding(top = 24.dp),
        )

        AuthLinkPrompt(
            prompt = stringResource(Res.string.have_account),
            action = stringResource(Res.string.login_now),
            enabled = !state.isLoading,
            onClick = { onEvent(SignUpEvent.LoginClicked) },
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
