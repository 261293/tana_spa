package com.tana.spa.feature.auth.presentation.manipulator

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.auth.domain.model.LoginResult
import com.tana.spa.feature.auth.domain.usecase.GetCurrentSessionUseCase
import com.tana.spa.feature.auth.domain.usecase.LoginUseCase
import kotlinx.coroutines.launch

class LoginViewModel(private val login: LoginUseCase, getCurrentSession: GetCurrentSessionUseCase) :
    MviViewModel<LoginState, LoginEvent, LoginEffect>(LoginState()) {

    init {
        // Already signed in from a previous launch: skip the form.
        if (getCurrentSession() != null) sendEffect(LoginEffect.NavigateHome)
    }

    override fun onEvent(event: LoginEvent) {
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
        if (state.value.isLoading) return
        val email = state.value.email
        val password = state.value.password
        setState { copy(isLoading = true, failure = null, emailError = null, passwordError = null) }
        viewModelScope.launch {
            when (val result = login(email, password)) {
                is LoginResult.Success -> {
                    setState { copy(isLoading = false) }
                    sendEffect(LoginEffect.NavigateHome)
                }

                is LoginResult.InvalidInput -> setState {
                    copy(isLoading = false, emailError = result.email, passwordError = result.password)
                }

                is LoginResult.Failure -> setState { copy(isLoading = false, failure = result.reason) }
            }
        }
    }
}
