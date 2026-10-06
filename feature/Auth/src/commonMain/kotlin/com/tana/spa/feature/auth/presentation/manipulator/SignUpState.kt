package com.tana.spa.feature.auth.presentation.manipulator

import com.tana.spa.feature.auth.domain.model.AuthFailure
import com.tana.spa.shared.auth.EmailError
import com.tana.spa.shared.auth.NameError
import com.tana.spa.shared.auth.PasswordError
import com.tana.spa.shared.auth.PhoneError

/** Errors are typed, not strings, so the UI layer picks the localised text. */
data class SignUpState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val nameError: NameError? = null,
    val emailError: EmailError? = null,
    val phoneError: PhoneError? = null,
    val passwordError: PasswordError? = null,
    /** Server/network failure shown under the form. */
    val failure: AuthFailure? = null,
    val isLoading: Boolean = false,
)

sealed interface SignUpEvent {
    data class NameChanged(val value: String) : SignUpEvent
    data class EmailChanged(val value: String) : SignUpEvent
    data class PhoneChanged(val value: String) : SignUpEvent
    data class PasswordChanged(val value: String) : SignUpEvent
    data object TogglePasswordVisibility : SignUpEvent
    data object Submit : SignUpEvent
    data object GoogleClicked : SignUpEvent
    data object LoginClicked : SignUpEvent
}

/** One-shot outputs that must not be replayed on recomposition or rotation. */
sealed interface SignUpEffect {
    /** The account exists and the user is signed in; next they confirm their phone number. */
    data object NavigateToVerifyPhone : SignUpEffect
    data object NavigateToLogin : SignUpEffect
    data class ShowComingSoon(val feature: ComingSoon) : SignUpEffect
}
