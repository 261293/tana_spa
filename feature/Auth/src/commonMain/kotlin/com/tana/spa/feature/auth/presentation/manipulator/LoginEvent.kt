package com.tana.spa.feature.auth.presentation.manipulator

sealed interface LoginEvent {
    data class EmailChanged(val value: String) : LoginEvent
    data class PasswordChanged(val value: String) : LoginEvent
    data object TogglePasswordVisibility : LoginEvent
    data object Submit : LoginEvent
    data object GoogleClicked : LoginEvent
    data object ForgotPasswordClicked : LoginEvent
    data object StaffSignInClicked : LoginEvent
    data object SignUpClicked : LoginEvent
}
