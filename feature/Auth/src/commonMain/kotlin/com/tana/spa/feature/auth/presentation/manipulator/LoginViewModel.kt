package com.tana.spa.feature.auth.presentation.manipulator

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.auth.domain.model.LoginResult
import com.tana.spa.feature.auth.domain.model.Session
import com.tana.spa.feature.auth.domain.model.UserRoleName
import com.tana.spa.feature.auth.domain.usecase.GetCurrentSessionUseCase
import com.tana.spa.feature.auth.domain.usecase.LoginUseCase
import com.tana.spa.shared.auth.AuthValidation
import kotlinx.coroutines.launch

class LoginViewModel(private val login: LoginUseCase, getCurrentSession: GetCurrentSessionUseCase) :
    MviViewModel<LoginState, LoginEvent, LoginEffect>(LoginState()) {

    init {
        // Already signed in from a previous launch: skip the form.
        getCurrentSession()?.let { session ->
            AppLog.i(logTag) { "existing valid session, skipping login form" }
            sendEffect(session.destination())
        }
    }

    override fun handleEvent(event: LoginEvent) {
        when (event) {
            // Editing a field clears its own error and any stale server failure.
            is LoginEvent.EmailChanged -> setState { copy(email = event.value, emailError = null, failure = null) }

            is LoginEvent.PasswordChanged ->
                setState { copy(password = event.value, passwordError = null, failure = null) }

            LoginEvent.EmailBlurred -> setState { copy(emailError = errorOnceTyped(email, AuthValidation::validateEmail)) }

            LoginEvent.PasswordBlurred ->
                setState { copy(passwordError = errorOnceTyped(password, AuthValidation::validatePassword)) }

            LoginEvent.TogglePasswordVisibility -> setState { copy(passwordVisible = !passwordVisible) }

            LoginEvent.Submit -> submit()

            LoginEvent.GoogleClicked -> sendEffect(LoginEffect.ShowComingSoon(ComingSoon.GOOGLE))

            LoginEvent.ForgotPasswordClicked -> sendEffect(LoginEffect.ShowComingSoon(ComingSoon.FORGOT_PASSWORD))

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
                    AppLog.i(logTag) { "login succeeded as ${result.session.user.role.wire}" }
                    setState { copy(isLoading = false) }
                    sendEffect(result.session.destination())
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

/**
 * The owner runs the spa from the console; everyone else lands in the guest app. The role is a
 * convenience only: the console's own calls are refused by the server for any other account.
 */
private fun Session.destination(): LoginEffect =
    if (user.role == UserRoleName.OWNER) LoginEffect.NavigateToDesk else LoginEffect.NavigateHome
