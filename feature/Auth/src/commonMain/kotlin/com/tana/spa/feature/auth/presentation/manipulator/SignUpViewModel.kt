package com.tana.spa.feature.auth.presentation.manipulator

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.auth.domain.model.RegisterResult
import com.tana.spa.feature.auth.domain.usecase.RegisterUseCase
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlags
import kotlinx.coroutines.launch

class SignUpViewModel(private val register: RegisterUseCase, private val featureFlags: FeatureFlags) :
    MviViewModel<SignUpState, SignUpEvent, SignUpEffect>(SignUpState()) {

    override fun handleEvent(event: SignUpEvent) {
        when (event) {
            // Editing a field clears its own error and any stale server failure.
            is SignUpEvent.NameChanged -> setState { copy(name = event.value, nameError = null, failure = null) }

            is SignUpEvent.EmailChanged -> setState { copy(email = event.value, emailError = null, failure = null) }

            is SignUpEvent.PhoneChanged -> setState { copy(phone = event.value, phoneError = null, failure = null) }

            is SignUpEvent.PasswordChanged ->
                setState { copy(password = event.value, passwordError = null, failure = null) }

            SignUpEvent.TogglePasswordVisibility -> setState { copy(passwordVisible = !passwordVisible) }

            SignUpEvent.Submit -> submit()

            SignUpEvent.GoogleClicked -> sendEffect(SignUpEffect.ShowComingSoon(ComingSoon.GOOGLE))

            SignUpEvent.LoginClicked -> sendEffect(SignUpEffect.NavigateToLogin)
        }
    }

    private fun submit() {
        // Double-tap / IME "Done" + button tap must not create two accounts.
        if (state.value.isLoading) {
            AppLog.d(logTag) { "submit ignored: already loading" }
            return
        }
        val form = state.value
        setState {
            copy(isLoading = true, failure = null, nameError = null, emailError = null, phoneError = null, passwordError = null)
        }
        viewModelScope.launch {
            when (val result = register(form.name, form.email, form.phone, form.password)) {
                is RegisterResult.Success -> {
                    AppLog.i(logTag) { "registration succeeded" }
                    setState { copy(isLoading = false) }
                    sendEffect(nextStepAfterRegistration())
                }

                is RegisterResult.InvalidInput -> {
                    AppLog.i(logTag) {
                        "registration rejected locally: name=${result.name != null} email=${result.email != null} " +
                            "phone=${result.phone != null} password=${result.password != null}"
                    }
                    setState {
                        copy(
                            isLoading = false,
                            nameError = result.name,
                            emailError = result.email,
                            phoneError = result.phone,
                            passwordError = result.password,
                        )
                    }
                }

                is RegisterResult.Failure -> {
                    AppLog.w(logTag) { "registration failed: ${result.reason}" }
                    setState { copy(isLoading = false, failure = result.reason) }
                }
            }
        }
    }

    private fun nextStepAfterRegistration(): SignUpEffect = if (featureFlags.isEnabled(FeatureFlag.PHONE_OTP_VERIFICATION)) {
        SignUpEffect.NavigateToVerifyPhone
    } else {
        AppLog.i(logTag) { "phone verification is switched off, skipping it" }
        SignUpEffect.NavigateHome
    }
}
