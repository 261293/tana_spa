package com.tana.spa.feature.booking.presentation.ui

import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.refusal_code
import com.tana.spa.feature.booking.resources.refusal_menu_changed
import com.tana.spa.feature.booking.resources.refusal_offline
import com.tana.spa.feature.booking.resources.refusal_other
import com.tana.spa.feature.booking.resources.refusal_settled
import com.tana.spa.feature.booking.resources.refusal_signed_out
import com.tana.spa.feature.booking.resources.refusal_slot_past
import com.tana.spa.feature.booking.resources.refusal_slot_taken
import com.tana.spa.feature.booking.resources.refusal_too_late
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.booking.BookingErrorCodes
import org.jetbrains.compose.resources.StringResource

/**
 * The spa's refusal, in words a guest can act on. A null [code] is nothing having reached the
 * spa at all, which asks for a different answer than a rule the spa applied.
 */
internal fun refusalText(code: String?): StringResource = when (code) {
    BookingErrorCodes.SLOT_TAKEN -> Res.string.refusal_slot_taken
    BookingErrorCodes.SLOT_IN_PAST -> Res.string.refusal_slot_past
    BookingErrorCodes.INVALID_CODE -> Res.string.refusal_code
    BookingErrorCodes.TOO_LATE_TO_CANCEL -> Res.string.refusal_too_late
    BookingErrorCodes.INVALID_TRANSITION, BookingErrorCodes.ALREADY_DECIDED -> Res.string.refusal_settled
    BookingErrorCodes.UNKNOWN_SERVICE, BookingErrorCodes.UNKNOWN_THERAPIST -> Res.string.refusal_menu_changed
    AuthErrorCodes.UNAUTHORIZED -> Res.string.refusal_signed_out
    null -> Res.string.refusal_offline
    else -> Res.string.refusal_other
}
