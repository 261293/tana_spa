package com.tana.spa.feature.auth.presentation.manipulator

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.auth.domain.model.OtpFailure
import com.tana.spa.feature.auth.domain.model.OtpSendResult
import com.tana.spa.feature.auth.domain.model.OtpVerifyResult
import com.tana.spa.feature.auth.domain.usecase.GetCurrentSessionUseCase
import com.tana.spa.feature.auth.domain.usecase.SendPhoneOtpUseCase
import com.tana.spa.feature.auth.domain.usecase.VerifyPhoneOtpUseCase
import com.tana.spa.shared.auth.AuthValidation
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlags
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VerifyPhoneViewModel(
    private val sendOtp: SendPhoneOtpUseCase,
    private val verifyOtp: VerifyPhoneOtpUseCase,
    getCurrentSession: GetCurrentSessionUseCase,
    featureFlags: FeatureFlags,
) : MviViewModel<VerifyPhoneState, VerifyPhoneEvent, VerifyPhoneEffect>(VerifyPhoneState()) {

    private var countdown: Job? = null

    // Not derived from state: a second tap must be ignored even before the first one's
    // `isSending` update has been observed.
    private var sendInFlight = false

    init {
        val session = getCurrentSession()
        when {
            // Before the session check: a switched-off step must not text anybody, and must not
            // bounce a signed-out visitor to login over something that no longer runs.
            !featureFlags.isEnabled(FeatureFlag.PHONE_OTP_VERIFICATION) -> {
                AppLog.i(logTag) { "phone verification is switched off, leaving the screen" }
                sendEffect(VerifyPhoneEffect.NavigateHome)
            }

            session == null -> {
                AppLog.w(logTag) { "no valid session, sending user back to login" }
                sendEffect(VerifyPhoneEffect.NavigateToLogin)
            }

            session.user.phoneVerified -> {
                AppLog.i(logTag) { "phone already verified, skipping" }
                sendEffect(VerifyPhoneEffect.NavigateHome)
            }

            // Arriving here means a code is wanted: send it without making the user ask.
            else -> send()
        }
    }

    override fun handleEvent(event: VerifyPhoneEvent) {
        when (event) {
            is VerifyPhoneEvent.CodeChanged -> onCodeChanged(event.value)

            VerifyPhoneEvent.Submit -> verify()

            VerifyPhoneEvent.Resend -> send()

            VerifyPhoneEvent.Skip -> {
                AppLog.i(logTag) { "user skipped phone verification" }
                sendEffect(VerifyPhoneEffect.NavigateHome)
            }
        }
    }

    private fun onCodeChanged(raw: String) {
        // Digits only, and never longer than a code (also covers pasting "123 456").
        val code = raw.filter { it.isDigit() }.take(AuthValidation.OTP_CODE_LENGTH)
        setState { copy(code = code, message = null) }
        if (code.length == AuthValidation.OTP_CODE_LENGTH) verify()
    }

    private fun send() {
        if (sendInFlight || state.value.resendSecondsLeft > 0) {
            AppLog.d(logTag) { "send ignored: inFlight=$sendInFlight cooldown=${state.value.resendSecondsLeft}s" }
            return
        }
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
                    AppLog.i(logTag) { "code sent: resendAfter=${result.resendAfterSeconds}s expiresIn=${result.expiresInSeconds}s" }
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

                OtpSendResult.AlreadyVerified -> {
                    AppLog.i(logTag) { "server says phone already verified" }
                    sendEffect(VerifyPhoneEffect.NavigateHome)
                }

                is OtpSendResult.RateLimited -> {
                    AppLog.w(logTag) { "send rate limited: retryAfter=${result.retryAfterSeconds}s" }
                    setState { copy(isSending = false) }
                    if (result.retryAfterSeconds <= MAX_COUNTDOWN_SECONDS) {
                        // Typically "a code was just sent": show the countdown instead of an error.
                        startCountdown(result.retryAfterSeconds)
                    } else {
                        setState { copy(message = VerifyMessage.RateLimited) }
                    }
                }

                is OtpSendResult.Failed -> {
                    AppLog.w(logTag) { "send failed: ${result.reason}" }
                    when (result.reason) {
                        OtpFailure.SESSION_EXPIRED -> sendEffect(VerifyPhoneEffect.NavigateToLogin)
                        OtpFailure.DISABLED -> sendEffect(VerifyPhoneEffect.NavigateHome)
                        else -> setState { copy(isSending = false, message = VerifyMessage.Failure(result.reason)) }
                    }
                }
            }
        }
    }

    private fun verify() {
        val current = state.value
        if (current.isVerifying || current.code.length != AuthValidation.OTP_CODE_LENGTH) return
        val code = current.code
        AppLog.i(logTag) { "verifying code" }
        setState { copy(isVerifying = true, message = null) }
        viewModelScope.launch {
            when (val result = verifyOtp(code)) {
                OtpVerifyResult.Verified -> {
                    AppLog.i(logTag) { "phone verified" }
                    setState { copy(isVerifying = false) }
                    sendEffect(VerifyPhoneEffect.NavigateHome)
                }

                is OtpVerifyResult.WrongCode -> {
                    AppLog.w(logTag) { "wrong code: attemptsRemaining=${result.attemptsRemaining}" }
                    setState { copy(isVerifying = false, code = "", message = VerifyMessage.WrongCode(result.attemptsRemaining)) }
                }

                is OtpVerifyResult.Failed -> {
                    AppLog.w(logTag) { "verify failed: ${result.reason}" }
                    when (result.reason) {
                        OtpFailure.SESSION_EXPIRED -> sendEffect(VerifyPhoneEffect.NavigateToLogin)

                        OtpFailure.DISABLED -> sendEffect(VerifyPhoneEffect.NavigateHome)

                        else -> setState {
                            copy(isVerifying = false, code = "", message = VerifyMessage.Failure(result.reason))
                        }
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
