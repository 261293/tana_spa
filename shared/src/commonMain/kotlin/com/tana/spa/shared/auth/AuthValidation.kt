package com.tana.spa.shared.auth

enum class EmailError { EMPTY, INVALID }

enum class PasswordError { EMPTY, TOO_SHORT }

enum class PhoneError { EMPTY, INVALID }

enum class NameError { EMPTY, TOO_LONG }

/**
 * Credential rules shared by the forms (instant feedback) and the server (authoritative
 * check), so the two can never drift apart.
 */
object AuthValidation {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 128
    const val MAX_EMAIL_LENGTH = 254
    const val MAX_NAME_LENGTH = 100

    /** Digits in a phone-verification code (Twilio Verify's default, and what the UI renders). */
    const val OTP_CODE_LENGTH = 6

    // Deliberately simple: the only real proof an address works is delivering mail to it.
    private val emailRegex = Regex("""^[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}$""")

    // Vietnamese mobile numbers: 0 + [35789] + 8 digits (e.g. 0333433969).
    private val vnLocalRegex = Regex("""^0[35789][0-9]{8}$""")
    private val vnInternationalRegex = Regex("""^84[35789][0-9]{8}$""")
    private val internationalRegex = Regex("""^[0-9]{8,15}$""")
    private const val PHONE_SEPARATORS = " .-()"

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun validateName(raw: String): NameError? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> NameError.EMPTY
            name.length > MAX_NAME_LENGTH -> NameError.TOO_LONG
            else -> null
        }
    }

    fun validateEmail(raw: String): EmailError? {
        val email = raw.trim()
        return when {
            email.isEmpty() -> EmailError.EMPTY
            email.length > MAX_EMAIL_LENGTH || !emailRegex.matches(email) -> EmailError.INVALID
            else -> null
        }
    }

    /** Passwords are never trimmed: leading/trailing spaces are legitimate characters. */
    fun validatePassword(password: String): PasswordError? = when {
        password.isEmpty() -> PasswordError.EMPTY
        password.length < MIN_PASSWORD_LENGTH -> PasswordError.TOO_SHORT
        else -> null
    }

    /**
     * Canonical `+<country><number>` form, or null when [raw] is not a usable phone number.
     *
     * Accepted: `0333433969`, `0333 433 969`, `84333433969`, `+84 333-433-969`, and other
     * international numbers written with a leading `+`. Canonicalising matters for uniqueness:
     * without it the same person could register as `0333…` and `+84333…`.
     */
    fun normalizePhone(raw: String): String? {
        val compact = raw.trim().filterNot { it in PHONE_SEPARATORS }
        return when {
            compact.isEmpty() -> null
            compact.startsWith("+") -> {
                val digits = compact.drop(1)
                val ok = if (digits.startsWith("84")) vnInternationalRegex.matches(digits)
                else internationalRegex.matches(digits)
                if (ok) "+$digits" else null
            }
            vnLocalRegex.matches(compact) -> "+84" + compact.drop(1)
            vnInternationalRegex.matches(compact) -> "+$compact"
            else -> null
        }
    }

    fun validatePhone(raw: String): PhoneError? = when {
        raw.isBlank() -> PhoneError.EMPTY
        normalizePhone(raw) == null -> PhoneError.INVALID
        else -> null
    }
}
