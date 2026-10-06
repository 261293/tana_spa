package com.tana.spa.feature.auth.domain.usecase

import com.tana.spa.feature.auth.domain.model.RegisterResult
import com.tana.spa.feature.auth.domain.repository.AuthOutcome
import com.tana.spa.feature.auth.domain.repository.AuthRepository
import com.tana.spa.shared.auth.AuthValidation

class RegisterUseCase(private val repository: AuthRepository) {

    suspend operator fun invoke(displayName: String, email: String, phone: String, password: String): RegisterResult {
        val nameError = AuthValidation.validateName(displayName)
        val emailError = AuthValidation.validateEmail(email)
        val phoneError = AuthValidation.validatePhone(phone)
        // Unlike login, the full length rule applies: this is where the password is chosen.
        val passwordError = AuthValidation.validatePassword(password)
        if (nameError != null || emailError != null || phoneError != null || passwordError != null) {
            return RegisterResult.InvalidInput(nameError, emailError, phoneError, passwordError)
        }
        // Sent as typed: the server normalises the phone (it knows every country's rules).
        val outcome = repository.register(displayName.trim(), AuthValidation.normalizeEmail(email), phone.trim(), password)
        return when (outcome) {
            is AuthOutcome.Success -> RegisterResult.Success(outcome.session)
            is AuthOutcome.Failure -> RegisterResult.Failure(outcome.reason)
        }
    }
}
