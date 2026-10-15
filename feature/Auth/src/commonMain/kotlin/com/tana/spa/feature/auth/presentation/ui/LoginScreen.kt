package com.tana.spa.feature.auth.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaSocialButton
import com.tana.spa.core.ui.components.SpaTextField
import com.tana.spa.feature.auth.presentation.manipulator.LoginEffect
import com.tana.spa.feature.auth.presentation.manipulator.LoginEvent
import com.tana.spa.feature.auth.presentation.manipulator.LoginState
import com.tana.spa.feature.auth.resources.Res
import com.tana.spa.feature.auth.resources.continue_with
import com.tana.spa.feature.auth.resources.email_label
import com.tana.spa.feature.auth.resources.email_placeholder
import com.tana.spa.feature.auth.resources.forgot_password
import com.tana.spa.feature.auth.resources.hide_password
import com.tana.spa.feature.auth.resources.ic_email
import com.tana.spa.feature.auth.resources.ic_eye
import com.tana.spa.feature.auth.resources.ic_eye_off
import com.tana.spa.feature.auth.resources.ic_google
import com.tana.spa.feature.auth.resources.ic_lock
import com.tana.spa.feature.auth.resources.login_button
import com.tana.spa.feature.auth.resources.login_intro
import com.tana.spa.feature.auth.resources.login_title
import com.tana.spa.feature.auth.resources.login_title_accent
import com.tana.spa.feature.auth.resources.login_with_google
import com.tana.spa.feature.auth.resources.no_account
import com.tana.spa.feature.auth.resources.password_label
import com.tana.spa.feature.auth.resources.password_placeholder
import com.tana.spa.feature.auth.resources.show_password
import com.tana.spa.feature.auth.resources.sign_up_now
import com.tana.spa.feature.auth.resources.staff_sign_in
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Figma "LOGIN" screen. Stateless: state comes in, [onEvent] goes out, effects are one-shot. */
@Composable
fun LoginScreen(
    state: LoginState,
    effects: Flow<LoginEffect>,
    onEvent: (LoginEvent) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToStaffConsole: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                LoginEffect.NavigateHome -> onNavigateHome()

                LoginEffect.NavigateToSignUp -> onNavigateToSignUp()

                LoginEffect.NavigateToStaffConsole -> onNavigateToStaffConsole()

                is LoginEffect.ShowComingSoon -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(getString(effect.feature.res()))
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuthShell(
            title = stringResource(Res.string.login_title),
            accent = stringResource(Res.string.login_title_accent),
            intro = stringResource(Res.string.login_intro),
        ) {
            LoginForm(state, onEvent)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) { data -> Snackbar(data) }
    }
}

@Composable
private fun ColumnScope.LoginForm(state: LoginState, onEvent: (LoginEvent) -> Unit) {
    val focusManager = LocalFocusManager.current

    SpaTextField(
        label = stringResource(Res.string.email_label),
        value = state.email,
        onValueChange = { onEvent(LoginEvent.EmailChanged(it)) },
        placeholder = stringResource(Res.string.email_placeholder),
        leadingIcon = painterResource(Res.drawable.ic_email),
        error = state.emailError.text(),
        enabled = !state.isLoading,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
    )

    val showPassword = state.passwordVisible
    SpaTextField(
        label = stringResource(Res.string.password_label),
        value = state.password,
        onValueChange = { onEvent(LoginEvent.PasswordChanged(it)) },
        placeholder = stringResource(Res.string.password_placeholder),
        leadingIcon = painterResource(Res.drawable.ic_lock),
        error = state.passwordError.text(),
        enabled = !state.isLoading,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus()
            onEvent(LoginEvent.Submit)
        }),
        trailing = {
            val description = stringResource(if (showPassword) Res.string.hide_password else Res.string.show_password)
            Image(
                painter = painterResource(if (showPassword) Res.drawable.ic_eye else Res.drawable.ic_eye_off),
                contentDescription = description,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(role = Role.Button) { onEvent(LoginEvent.TogglePasswordVisibility) },
            )
        },
    )

    state.failure.text()?.let { message ->
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = SpaTheme.textStyles.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Text(
        text = stringResource(Res.string.forgot_password),
        style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .align(Alignment.End)
            .clickable(enabled = !state.isLoading, role = Role.Button) { onEvent(LoginEvent.ForgotPasswordClicked) }
            .padding(vertical = 4.dp),
    )

    SpaPrimaryButton(
        text = stringResource(Res.string.login_button),
        onClick = {
            focusManager.clearFocus()
            onEvent(LoginEvent.Submit)
        },
        loading = state.isLoading,
    )

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        Text(
            text = stringResource(Res.string.continue_with),
            style = SpaTheme.textStyles.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
    }

    SpaSocialButton(
        text = stringResource(Res.string.login_with_google),
        icon = painterResource(Res.drawable.ic_google),
        onClick = { onEvent(LoginEvent.GoogleClicked) },
        enabled = !state.isLoading,
    )

    AuthLinkPrompt(
        prompt = stringResource(Res.string.no_account),
        action = stringResource(Res.string.sign_up_now),
        enabled = !state.isLoading,
        onClick = { onEvent(LoginEvent.SignUpClicked) },
        modifier = Modifier.fillMaxWidth(),
    )

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(Res.string.staff_sign_in),
            style = SpaTheme.textStyles.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(enabled = !state.isLoading, role = Role.Button) { onEvent(LoginEvent.StaffSignInClicked) }
                .padding(8.dp),
        )
    }
}
