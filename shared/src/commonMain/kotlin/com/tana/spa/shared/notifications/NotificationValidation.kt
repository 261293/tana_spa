package com.tana.spa.shared.notifications

enum class DeviceTokenError { EMPTY, TOO_SHORT, TOO_LONG, UNSUPPORTED_CHARACTER }

enum class NotificationTextError { EMPTY, TOO_LONG }

enum class NotificationDataError { TOO_MANY_ENTRIES, KEY_EMPTY, VALUE_TOO_LONG }

/**
 * Limits shared by the clients (which refuse to send what the server would reject) and the server
 * (authoritative), kept in step with the column constraints in `V8__push_notifications.sql`.
 */
object NotificationValidation {
    const val MAX_TITLE_LENGTH = 120
    const val MAX_BODY_LENGTH = 500

    /** FCM registration tokens are ~160 characters today and the format is not promised to hold. */
    const val MIN_DEVICE_TOKEN_LENGTH = 16
    const val MAX_DEVICE_TOKEN_LENGTH = 4096

    const val MAX_DATA_ENTRIES = 16
    const val MAX_DATA_KEY_LENGTH = 64
    const val MAX_DATA_VALUE_LENGTH = 512

    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 50

    /**
     * A token goes into log lines and a JSON body, so anything outside the printable ASCII the
     * providers use is refused rather than escaped.
     */
    private val tokenRegex = Regex("""^[A-Za-z0-9_:.~%+\-/=]+$""")

    fun validateDeviceToken(raw: String): DeviceTokenError? {
        val token = raw.trim()
        return when {
            token.isEmpty() -> DeviceTokenError.EMPTY
            token.length < MIN_DEVICE_TOKEN_LENGTH -> DeviceTokenError.TOO_SHORT
            token.length > MAX_DEVICE_TOKEN_LENGTH -> DeviceTokenError.TOO_LONG
            !tokenRegex.matches(token) -> DeviceTokenError.UNSUPPORTED_CHARACTER
            else -> null
        }
    }

    fun validateTitle(raw: String): NotificationTextError? {
        val title = raw.trim()
        return when {
            title.isEmpty() -> NotificationTextError.EMPTY
            title.length > MAX_TITLE_LENGTH -> NotificationTextError.TOO_LONG
            else -> null
        }
    }

    fun validateBody(raw: String?): NotificationTextError? {
        val body = raw?.trim() ?: return null
        return if (body.length > MAX_BODY_LENGTH) NotificationTextError.TOO_LONG else null
    }

    fun validateData(data: Map<String, String>): NotificationDataError? = when {
        data.size > MAX_DATA_ENTRIES -> NotificationDataError.TOO_MANY_ENTRIES
        data.keys.any { it.isBlank() || it.length > MAX_DATA_KEY_LENGTH } -> NotificationDataError.KEY_EMPTY
        data.values.any { it.length > MAX_DATA_VALUE_LENGTH } -> NotificationDataError.VALUE_TOO_LONG
        else -> null
    }

    /** Clamped rather than rejected: a bad page size is not worth failing a read over. */
    fun pageSize(requested: Int?): Int = when {
        requested == null || requested <= 0 -> DEFAULT_PAGE_SIZE
        else -> minOf(requested, MAX_PAGE_SIZE)
    }
}
