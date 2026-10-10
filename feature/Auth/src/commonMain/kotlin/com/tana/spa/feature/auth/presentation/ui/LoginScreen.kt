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
import com.tana.spa.feature.auth.presentation.manipulator.LoginEffect
import com.tana.spa.feature.auth.presentation.manipulator.LoginEvent
import com.tana.spa.feature.auth.presentation.manipulator.LoginState
import com.tana.spa.feature.auth.resources.Res
import com.tana.spa.feature.auth.resources.email_label
import com.tana.spa.feature.auth.resources.email_placeholder
import com.tana.spa.feature.auth.resources.hide_password
import com.tana.spa.feature.auth.resources.ic_email
import com.tana.spa.feature.auth.resources.ic_eye
import com.tana.spa.feature.auth.resources.ic_eye_off
import com.tana.spa.feature.auth.resources.ic_google
import com.tana.spa.feature.auth.resources.ic_lock
import com.tana.spa.feature.auth.resources.login_button
import com.tana.spa.feature.auth.resources.login_title
import com.tana.spa.feature.auth.resources.login_with_google
import com.tana.spa.feature.auth.resources.no_account
import com.tana.spa.feature.auth.resources.password_label
import com.tana.spa.feature.auth.resources.password_placeholder
import com.tana.spa.feature.auth.resources.show_password
import com.tana.spa.feature.auth.resources.sign_up_now
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
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                LoginEffect.NavigateHome -> onNavigateHome()

                LoginEffect.NavigateToSignUp -> onNavigateToSignUp()

                is LoginEffect.ShowComingSoon -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(getString(effect.feature.res()))
                }
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
        LoginForm(state = state, onEvent = onEvent)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) { data -> Snackbar(data) }
    }
}

@Composable
private fun LoginForm(state: LoginState, onEvent: (LoginEvent) -> Unit) {
    val focusManager = LocalFocusManager.current

    // The column scrolls when the keyboard or a short window leaves too little room, and is
    // centred (by the parent Box) when there is plenty. widthIn keeps it phone-sized on web/tablets.
    Column(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 15.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.login_title),
            style = SpaTheme.textStyles.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )

        Column(
            modifier = Modifier.padding(top = 11.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
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
                    val description =
                        stringResource(if (showPassword) Res.string.hide_password else Res.string.show_password)
                    Image(
                        painter = painterResource(if (showPassword) Res.drawable.ic_eye else Res.drawable.ic_eye_off),
                        contentDescription = description,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(role = Role.Button) { onEvent(LoginEvent.TogglePasswordVisibility) },
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
            text = stringResource(Res.string.login_button),
            onClick = {
                focusManager.clearFocus()
                onEvent(LoginEvent.Submit)
            },
            loading = state.isLoading,
            modifier = Modifier.padding(top = 28.dp),
        )

        SpaSocialButton(
            text = stringResource(Res.string.login_with_google),
            icon = painterResource(Res.drawable.ic_google),
            onClick = { onEvent(LoginEvent.GoogleClicked) },
            enabled = !state.isLoading,
            modifier = Modifier.padding(top = 28.dp),
        )

        AuthLinkPrompt(
            prompt = stringResource(Res.string.no_account),
            action = stringResource(Res.string.sign_up_now),
            enabled = !state.isLoading,
            onClick = { onEvent(LoginEvent.SignUpClicked) },
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
