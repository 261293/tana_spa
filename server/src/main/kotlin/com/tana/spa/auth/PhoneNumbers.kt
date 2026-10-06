package com.tana.spa.auth

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

data class ParsedPhone(
    /** Canonical `+<country><number>`. */
    val e164: String,
    /** ISO 3166-1 alpha-2 region the number belongs to (VN, US, ...). */
    val region: String,
)

/**
 * Authoritative, worldwide phone validation (Google's libphonenumber metadata for every country).
 * The shared module's lighter check is for instant form feedback only; this is the real gate.
 */
object PhoneNumbers {
    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    /**
     * @param defaultRegion used when [raw] has no `+countrycode`, so a Vietnamese user can type `0333 433 969`.
     * @return null for anything that is not a real, SMS-capable number of a known region.
     */
    fun parse(raw: String, defaultRegion: String = "VN"): ParsedPhone? {
        val text = raw.trim()
        if (text.isEmpty() || text.length > MAX_INPUT) return null
        return try {
            val number = util.parse(text, defaultRegion)
            // Extensions ("... ext 12") cannot receive an SMS; silently dropping one would be surprising.
            if (number.hasExtension() || !util.isValidNumber(number)) return null
            val region = util.getRegionCodeForNumber(number)
            // "001" = non-geographic (satellite, premium services): not a person's phone.
            if (region == null || region == "001") return null
            ParsedPhone(util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164), region)
        } catch (_: NumberParseException) {
            null
        }
    }

    /**
     * `+84333433969` -> `+84••••••969`: the country code and last three digits, enough for
     * "we sent a code to ..." without exposing the number. The country code's length varies
     * (`+1` vs `+84` vs `+353`), so it is read from the number rather than assumed.
     */
    fun mask(e164: String): String {
        val head = try {
            "+" + util.parse(e164, null).countryCode
        } catch (_: NumberParseException) {
            return e164.take(1) + "•".repeat((e164.length - 1 - VISIBLE_TAIL).coerceAtLeast(0)) + e164.takeLast(VISIBLE_TAIL)
        }
        val hidden = e164.length - head.length - VISIBLE_TAIL
        return if (hidden < 1) e164 else head + "•".repeat(hidden) + e164.takeLast(VISIBLE_TAIL)
    }

    private const val MAX_INPUT = 32
    private const val VISIBLE_TAIL = 3
}
