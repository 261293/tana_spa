package com.tana.spa.feature.auth.domain.usecase

import com.tana.spa.feature.auth.domain.model.OtpSendResult
import com.tana.spa.feature.auth.domain.model.OtpVerifyResult
import com.tana.spa.feature.auth.domain.repository.AuthRepository

class SendPhoneOtpUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): OtpSendResult = repository.sendPhoneOtp()
}

class VerifyPhoneOtpUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(code: String): OtpVerifyResult = repository.verifyPhoneOtp(code.trim())
}
