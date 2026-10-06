package com.tana.spa.feature.auth.presentation.ui

import androidx.compose.runtime.Composable
import com.tana.spa.feature.auth.domain.model.AuthFailure
import com.tana.spa.feature.auth.domain.model.OtpFailure
import com.tana.spa.feature.auth.presentation.manipulator.ComingSoon
import com.tana.spa.feature.auth.presentation.manipulator.VerifyMessage
import com.tana.spa.feature.auth.resources.Res
import com.tana.spa.feature.auth.resources.coming_soon_google
import com.tana.spa.feature.auth.resources.error_email_empty
import com.tana.spa.feature.auth.resources.error_email_invalid
import com.tana.spa.feature.auth.resources.error_name_empty
import com.tana.spa.feature.auth.resources.error_name_too_long
import com.tana.spa.feature.auth.resources.error_password_empty
import com.tana.spa.feature.auth.resources.error_password_too_short
import com.tana.spa.feature.auth.resources.error_phone_empty
import com.tana.spa.feature.auth.resources.error_phone_invalid
import com.tana.spa.feature.auth.resources.failure_email_taken
import com.tana.spa.feature.auth.resources.failure_invalid_credentials
import com.tana.spa.feature.auth.resources.failure_invalid_input
import com.tana.spa.feature.auth.resources.failure_network
import com.tana.spa.feature.auth.resources.failure_phone_taken
import com.tana.spa.feature.auth.resources.failure_server
import com.tana.spa.feature.auth.resources.failure_too_many_requests
import com.tana.spa.feature.auth.resources.otp_failure_country
import com.tana.spa.feature.auth.resources.otp_failure_expired
import com.tana.spa.feature.auth.resources.otp_failure_not_requested
import com.tana.spa.feature.auth.resources.otp_failure_phone_claimed
import com.tana.spa.feature.auth.resources.otp_failure_too_many_attempts
import com.tana.spa.feature.auth.resources.otp_failure_unavailable
import com.tana.spa.feature.auth.resources.otp_rate_limited
import com.tana.spa.feature.auth.resources.otp_wrong_code
import com.tana.spa.feature.auth.resources.otp_wrong_code_attempts
import com.tana.spa.shared.auth.EmailError
import com.tana.spa.shared.auth.NameError
import com.tana.spa.shared.auth.PasswordError
import com.tana.spa.shared.auth.PhoneError
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal fun NameError.res(): StringResource = when (this) {
    NameError.EMPTY -> Res.string.error_name_empty
    NameError.TOO_LONG -> Res.string.error_name_too_long
}

internal fun EmailError.res(): StringResource = when (this) {
    EmailError.EMPTY -> Res.string.error_email_empty
    EmailError.INVALID -> Res.string.error_email_invalid
}

internal fun PhoneError.res(): StringResource = when (this) {
    PhoneError.EMPTY -> Res.string.error_phone_empty
    PhoneError.INVALID -> Res.string.error_phone_invalid
}

internal fun PasswordError.res(): StringResource = when (this) {
    PasswordError.EMPTY -> Res.string.error_password_empty
    PasswordError.TOO_SHORT -> Res.string.error_password_too_short
}

internal fun AuthFailure.res(): StringResource = when (this) {
    AuthFailure.INVALID_CREDENTIALS -> Res.string.failure_invalid_credentials
    AuthFailure.EMAIL_TAKEN -> Res.string.failure_email_taken
    AuthFailure.PHONE_TAKEN -> Res.string.failure_phone_taken
    AuthFailure.INVALID_INPUT -> Res.string.failure_invalid_input
    AuthFailure.TOO_MANY_REQUESTS -> Res.string.failure_too_many_requests
    AuthFailure.NETWORK -> Res.string.failure_network
    AuthFailure.SERVER -> Res.string.failure_server
}

internal fun OtpFailure.res(): StringResource = when (this) {
    // SESSION_EXPIRED navigates to login instead of being shown; the text only guards exhaustiveness.
    OtpFailure.SESSION_EXPIRED, OtpFailure.SERVER -> Res.string.failure_server
    OtpFailure.PHONE_CLAIMED -> Res.string.otp_failure_phone_claimed
    OtpFailure.COUNTRY_UNSUPPORTED -> Res.string.otp_failure_country
    OtpFailure.UNAVAILABLE -> Res.string.otp_failure_unavailable
    OtpFailure.NETWORK -> Res.string.failure_network
    OtpFailure.EXPIRED -> Res.string.otp_failure_expired
    OtpFailure.TOO_MANY_ATTEMPTS -> Res.string.otp_failure_too_many_attempts
    OtpFailure.NOT_REQUESTED -> Res.string.otp_failure_not_requested
}

internal fun ComingSoon.res(): StringResource = when (this) {
    ComingSoon.GOOGLE -> Res.string.coming_soon_google
}

@Composable
internal fun NameError?.text(): String? = this?.let { stringResource(it.res()) }

@Composable
internal fun EmailError?.text(): String? = this?.let { stringResource(it.res()) }

@Composable
internal fun PhoneError?.text(): String? = this?.let { stringResource(it.res()) }

@Composable
internal fun PasswordError?.text(): String? = this?.let { stringResource(it.res()) }

@Composable
internal fun AuthFailure?.text(): String? = this?.let { stringResource(it.res()) }

@Composable
internal fun VerifyMessage?.text(): String? = when (this) {
    null -> null
    VerifyMessage.RateLimited -> stringResource(Res.string.otp_rate_limited)
    is VerifyMessage.Failure -> stringResource(reason.res())
    is VerifyMessage.WrongCode ->
        if (attemptsRemaining != null) {
            pluralStringResource(Res.plurals.otp_wrong_code_attempts, attemptsRemaining, attemptsRemaining)
        } else {
            stringResource(Res.string.otp_wrong_code)
        }
}
