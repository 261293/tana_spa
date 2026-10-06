package com.tana.spa.feature.auth.domain.usecase

import com.tana.spa.feature.auth.domain.model.Session
import com.tana.spa.feature.auth.domain.repository.AuthRepository

class GetCurrentSessionUseCase(private val repository: AuthRepository) {
    operator fun invoke(): Session? = repository.currentSession()
}
