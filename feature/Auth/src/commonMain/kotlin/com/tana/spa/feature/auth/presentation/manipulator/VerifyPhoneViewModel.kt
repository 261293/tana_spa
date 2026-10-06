package com.tana.spa.feature.auth.presentation.manipulator

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.auth.domain.model.OtpFailure
import com.tana.spa.feature.auth.domain.model.OtpSendResult
import com.tana.spa.feature.auth.domain.model.OtpVerifyResult
import com.tana.spa.feature.auth.domain.usecase.GetCurrentSessionUseCase
import com.tana.spa.feature.auth.domain.usecase.SendPhoneOtpUseCase
import com.tana.spa.feature.auth.domain.usecase.VerifyPhoneOtpUseCase
import com.tana.spa.shared.auth.AuthValidation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VerifyPhoneViewModel(
    private val sendOtp: SendPhoneOtpUseCase,
    private val verifyOtp: VerifyPhoneOtpUseCase,
    getCurrentSession: GetCurrentSessionUseCase,
) : MviViewModel<VerifyPhoneState, VerifyPhoneEvent, VerifyPhoneEffect>(VerifyPhoneState()) {

    private var countdown: Job? = null

    // Not derived from state: a second tap must be ignored even before the first one's
    // `isSending` update has been observed.
    private var sendInFlight = false

    init {
        val session = getCurrentSession()
        when {
            session == null -> sendEffect(VerifyPhoneEffect.NavigateToLogin)
            session.user.phoneVerified -> sendEffect(VerifyPhoneEffect.NavigateHome)
            // Arriving here means a code is wanted: send it without making the user ask.
            else -> send()
        }
    }

    override fun onEvent(event: VerifyPhoneEvent) {
        when (event) {
            is VerifyPhoneEvent.CodeChanged -> onCodeChanged(event.value)
            VerifyPhoneEvent.Submit -> verify()
            VerifyPhoneEvent.Resend -> send()
            VerifyPhoneEvent.Skip -> sendEffect(VerifyPhoneEffect.NavigateHome)
        }
    }

    private fun onCodeChanged(raw: String) {
        // Digits only, and never longer than a code (also covers pasting "123 456").
        val code = raw.filter { it.isDigit() }.take(AuthValidation.OTP_CODE_LENGTH)
        setState { copy(code = code, message = null) }
        if (code.length == AuthValidation.OTP_CODE_LENGTH) verify()
    }

    private fun send() {
        if (sendInFlight || state.value.resendSecondsLeft > 0) return
        sendInFlight = true
        setState { copy(isSending = true, message = null) }
        viewModelScope.launch {
            val result = try {
                sendOtp()
            } finally {
                sendInFlight = false
            }
            when (result) {
                is OtpSendResult.Sent -> {
                    setState {
                        copy(
                            isSending = false,
                            maskedPhone = result.maskedPhone,
                            expiresInMinutes = (result.expiresInSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE,
                            code = "",
                            message = null,
                        )
                    }
                    startCountdown(result.resendAfterSeconds)
                }
                OtpSendResult.AlreadyVerified -> sendEffect(VerifyPhoneEffect.NavigateHome)
                is OtpSendResult.RateLimited -> {
                    setState { copy(isSending = false) }
                    if (result.retryAfterSeconds <= MAX_COUNTDOWN_SECONDS) {
                        // Typically "a code was just sent": show the countdown instead of an error.
                        startCountdown(result.retryAfterSeconds)
                    } else {
                        setState { copy(message = VerifyMessage.RateLimited) }
                    }
                }
                is OtpSendResult.Failed -> {
                    if (result.reason == OtpFailure.SESSION_EXPIRED) {
                        sendEffect(VerifyPhoneEffect.NavigateToLogin)
                    } else {
                        setState { copy(isSending = false, message = VerifyMessage.Failure(result.reason)) }
                    }
                }
            }
        }
    }

    private fun verify() {
        val current = state.value
        if (current.isVerifying || current.code.length != AuthValidation.OTP_CODE_LENGTH) return
        val code = current.code
        setState { copy(isVerifying = true, message = null) }
        viewModelScope.launch {
            when (val result = verifyOtp(code)) {
                OtpVerifyResult.Verified -> {
                    setState { copy(isVerifying = false) }
                    sendEffect(VerifyPhoneEffect.NavigateHome)
                }
                is OtpVerifyResult.WrongCode ->
                    setState { copy(isVerifying = false, code = "", message = VerifyMessage.WrongCode(result.attemptsRemaining)) }
                is OtpVerifyResult.Failed -> {
                    if (result.reason == OtpFailure.SESSION_EXPIRED) {
                        sendEffect(VerifyPhoneEffect.NavigateToLogin)
                    } else {
                        setState { copy(isVerifying = false, code = "", message = VerifyMessage.Failure(result.reason)) }
                    }
                }
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                setState { copy(resendSecondsLeft = left) }
                delay(ONE_SECOND_MILLIS)
                left--
            }
            setState { copy(resendSecondsLeft = 0) }
        }
    }

    private companion object {
        const val ONE_SECOND_MILLIS = 1_000L
        const val SECONDS_PER_MINUTE = 60

        /** Beyond this the wait is "come back later", not something to count down on screen. */
        const val MAX_COUNTDOWN_SECONDS = 120
    }
}
