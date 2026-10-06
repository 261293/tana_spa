package com.tana.spa.feature.auth.presentation.manipulator

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.auth.domain.model.LoginResult
import com.tana.spa.feature.auth.domain.usecase.GetCurrentSessionUseCase
import com.tana.spa.feature.auth.domain.usecase.LoginUseCase
import kotlinx.coroutines.launch

class LoginViewModel(private val login: LoginUseCase, getCurrentSession: GetCurrentSessionUseCase) :
    MviViewModel<LoginState, LoginEvent, LoginEffect>(LoginState()) {

    init {
        // Already signed in from a previous launch: skip the form.
        if (getCurrentSession() != null) {
            AppLog.i(logTag) { "existing valid session, skipping login form" }
            sendEffect(LoginEffect.NavigateHome)
        }
    }

    override fun handleEvent(event: LoginEvent) {
        when (event) {
            // Editing a field clears its own error and any stale server failure.
            is LoginEvent.EmailChanged -> setState { copy(email = event.value, emailError = null, failure = null) }

            is LoginEvent.PasswordChanged ->
                setState { copy(password = event.value, passwordError = null, failure = null) }

            LoginEvent.TogglePasswordVisibility -> setState { copy(passwordVisible = !passwordVisible) }

            LoginEvent.Submit -> submit()

            LoginEvent.GoogleClicked -> sendEffect(LoginEffect.ShowComingSoon(ComingSoon.GOOGLE))

            LoginEvent.SignUpClicked -> sendEffect(LoginEffect.NavigateToSignUp)
        }
    }

    private fun submit() {
        // Double-tap / IME "Done" + button tap must not fire two requests.
        if (state.value.isLoading) {
            AppLog.d(logTag) { "submit ignored: already loading" }
            return
        }
        val email = state.value.email
        val password = state.value.password
        setState { copy(isLoading = true, failure = null, emailError = null, passwordError = null) }
        viewModelScope.launch {
            when (val result = login(email, password)) {
                is LoginResult.Success -> {
                    AppLog.i(logTag) { "login succeeded" }
                    setState { copy(isLoading = false) }
                    sendEffect(LoginEffect.NavigateHome)
                }

                is LoginResult.InvalidInput -> {
                    // Which fields failed validation is useful; their values are not logged.
                    AppLog.i(logTag) {
                        "login rejected locally: emailError=${result.email != null} passwordError=${result.password != null}"
                    }
                    setState {
                        copy(isLoading = false, emailError = result.email, passwordError = result.password)
                    }
                }

                is LoginResult.Failure -> {
                    AppLog.w(logTag) { "login failed: ${result.reason}" }
                    setState { copy(isLoading = false, failure = result.reason) }
                }
            }
        }
    }
}
