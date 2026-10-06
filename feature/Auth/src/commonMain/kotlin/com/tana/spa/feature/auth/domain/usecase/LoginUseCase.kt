package com.tana.spa.feature.auth.domain.usecase

import com.tana.spa.feature.auth.domain.model.LoginResult
import com.tana.spa.feature.auth.domain.repository.AuthOutcome
import com.tana.spa.feature.auth.domain.repository.AuthRepository
import com.tana.spa.shared.auth.AuthValidation
import com.tana.spa.shared.auth.PasswordError

class LoginUseCase(private val repository: AuthRepository) {

    suspend operator fun invoke(email: String, password: String): LoginResult {
        val emailError = AuthValidation.validateEmail(email)
        // Only "not empty" is enforced here: the length policy applies when choosing a password,
        // and telling a user their existing password is "too short" would be misleading.
        val passwordError = if (password.isEmpty()) PasswordError.EMPTY else null
        if (emailError != null || passwordError != null) {
            return LoginResult.InvalidInput(emailError, passwordError)
        }
        return when (val outcome = repository.login(AuthValidation.normalizeEmail(email), password)) {
            is AuthOutcome.Success -> LoginResult.Success(outcome.session)
            is AuthOutcome.Failure -> LoginResult.Failure(outcome.reason)
        }
    }
}
