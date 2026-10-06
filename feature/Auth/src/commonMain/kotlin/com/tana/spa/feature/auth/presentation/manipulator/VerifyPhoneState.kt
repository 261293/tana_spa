package com.tana.spa.feature.auth.presentation.manipulator

import com.tana.spa.feature.auth.domain.model.OtpFailure

data class VerifyPhoneState(
    /** e.g. `+84••••••969`, known once the first code has been sent. */
    val maskedPhone: String? = null,
    /** How long the current code stays valid, for "the code expires in N minutes". */
    val expiresInMinutes: Int? = null,
    val code: String = "",
    /** True while the first code is being requested on screen open, and during every resend. */
    val isSending: Boolean = true,
    val isVerifying: Boolean = false,
    /** Seconds until "resend" unlocks; 0 means it can be pressed. */
    val resendSecondsLeft: Int = 0,
    val message: VerifyMessage? = null,
)

/** What went wrong (or is worth saying), as data; the UI picks the wording. */
sealed interface VerifyMessage {
    data class WrongCode(val attemptsRemaining: Int?) : VerifyMessage

    /** Asked for too many codes; try later (longer than a countdown can sensibly show). */
    data object RateLimited : VerifyMessage
    data class Failure(val reason: OtpFailure) : VerifyMessage
}

sealed interface VerifyPhoneEvent {
    data class CodeChanged(val value: String) : VerifyPhoneEvent
    data object Submit : VerifyPhoneEvent
    data object Resend : VerifyPhoneEvent

    /** "Để sau": keep the account, confirm the number another time. */
    data object Skip : VerifyPhoneEvent
}

sealed interface VerifyPhoneEffect {
    /** Verified (or skipped, or already verified): carry on into the app. */
    data object NavigateHome : VerifyPhoneEffect

    /** The login is gone; there is nothing to verify without it. */
    data object NavigateToLogin : VerifyPhoneEffect
}
