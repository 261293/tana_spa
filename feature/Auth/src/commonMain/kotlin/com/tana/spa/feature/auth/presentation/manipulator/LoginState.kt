package com.tana.spa.feature.auth.presentation.manipulator

import com.tana.spa.feature.auth.domain.model.AuthFailure
import com.tana.spa.shared.auth.EmailError
import com.tana.spa.shared.auth.PasswordError

/** Errors are typed, not strings, so the UI layer picks the localised text. */
data class LoginState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val emailError: EmailError? = null,
    val passwordError: PasswordError? = null,
    /** Server/network failure shown under the form. */
    val failure: AuthFailure? = null,
    val isLoading: Boolean = false,
)

/** One-shot outputs that must not be replayed on recomposition or rotation. */
sealed interface LoginEffect {
    data object NavigateHome : LoginEffect
    data object NavigateToSignUp : LoginEffect
    data class ShowComingSoon(val feature: ComingSoon) : LoginEffect
}

/** Features whose button exists in the design but whose flow is not built yet. */
enum class ComingSoon { GOOGLE }
