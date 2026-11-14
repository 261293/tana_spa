package com.tana.spa.shared.booking

enum class TextFieldError { EMPTY, TOO_LONG }

enum class DurationError { NOT_POSITIVE, TOO_LONG, NOT_A_MULTIPLE }

enum class PriceError { NEGATIVE, TOO_LARGE }

/**
 * What the owner may type into a new treatment or therapist. Compiled into the form (per-field
 * messages while typing) and into the routes (the authoritative check), so the two cannot drift.
 */
object SpaValidation {
    const val MAX_NAME_LENGTH = 80
    const val MAX_DESCRIPTION_LENGTH = 240
    const val MAX_FOCUS_LENGTH = 80
    const val MAX_BRAND_LENGTH = 60
    const val MAX_SIZE_LENGTH = 30

    /** The diary is a grid of quarter hours, so a treatment has to land on one. */
    const val DURATION_STEP_MINUTES = 15
    const val MAX_DURATION_MINUTES = 480
    const val MAX_PRICE = 100_000_000

    /** The field names the server names in a refusal, and the form matches against. */
    const val FIELD_NAME = "name"
    const val FIELD_DESCRIPTION = "description"
    const val FIELD_MINUTES = "minutes"
    const val FIELD_PRICE = "price"
    const val FIELD_FOCUS = "focus"
    const val FIELD_BRAND = "brand"
    const val FIELD_SIZE = "size"

    fun validateName(raw: String): TextFieldError? = text(raw, MAX_NAME_LENGTH)

    fun validateDescription(raw: String): TextFieldError? = text(raw, MAX_DESCRIPTION_LENGTH)

    fun validateFocus(raw: String): TextFieldError? = text(raw, MAX_FOCUS_LENGTH)

    fun validateMinutes(minutes: Int): DurationError? = when {
        minutes <= 0 -> DurationError.NOT_POSITIVE
        minutes > MAX_DURATION_MINUTES -> DurationError.TOO_LONG
        minutes % DURATION_STEP_MINUTES != 0 -> DurationError.NOT_A_MULTIPLE
        else -> null
    }

    fun validatePrice(price: Int): PriceError? = when {
        price < 0 -> PriceError.NEGATIVE
        price > MAX_PRICE -> PriceError.TOO_LARGE
        else -> null
    }

    /** The first field that fails, so a refusal can name it; null when the treatment is valid. */
    fun firstInvalidField(request: CreateServiceRequest): String? = when {
        validateName(request.name) != null -> FIELD_NAME
        validateDescription(request.description) != null -> FIELD_DESCRIPTION
        validateMinutes(request.minutes) != null -> FIELD_MINUTES
        validatePrice(request.price) != null -> FIELD_PRICE
        else -> null
    }

    fun validateLabel(raw: String, max: Int): TextFieldError? = if (raw.trim().length > max) TextFieldError.TOO_LONG else null

    fun firstInvalidField(request: CreateProductRequest): String? = when {
        validateName(request.name) != null -> FIELD_NAME
        validateLabel(request.brand, MAX_BRAND_LENGTH) != null -> FIELD_BRAND
        validateDescription(request.description) != null -> FIELD_DESCRIPTION
        validatePrice(request.price) != null -> FIELD_PRICE
        validateLabel(request.size, MAX_SIZE_LENGTH) != null -> FIELD_SIZE
        else -> null
    }

    fun firstInvalidField(request: CreateTherapistRequest): String? = when {
        validateName(request.name) != null -> FIELD_NAME
        validateFocus(request.focus) != null -> FIELD_FOCUS
        else -> null
    }

    /**
     * "Warm Stone Massage" to "warm-stone-massage". The server still has to make it unique: two
     * treatments may legitimately be named the same.
     */
    fun slugify(raw: String): String = raw.trim().lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .split('-')
        .filter { it.isNotEmpty() }
        .joinToString("-")
        .take(MAX_NAME_LENGTH)

    private fun text(raw: String, max: Int): TextFieldError? {
        val value = raw.trim()
        return when {
            value.isEmpty() -> TextFieldError.EMPTY
            value.length > max -> TextFieldError.TOO_LONG
            else -> null
        }
    }
}
