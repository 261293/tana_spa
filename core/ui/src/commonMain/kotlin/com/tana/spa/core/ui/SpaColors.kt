package com.tana.spa.core.ui

import androidx.compose.ui.graphics.Color

/**
 * Design tokens from the Figma file ("Jade Bathhouse", Moss & Linen): forest greens on mint
 * cream, dusk-lavender text, and tonal layering instead of outlines.
 */
object SpaColors {
    val Background = Color(0xFFF7FFF6)

    /** The tonal panel every card sits on. Lighter surfaces come from [Tone1] and [Linen]. */
    val Surface = Color(0xFFEAF5E8)

    val Primary = Color(0xFF117034)
    val OnPrimary = Color(0xFFF7FFF6)
    val PrimaryDark = Color(0xFF0E5A2A)

    /** Light moss, used for the action inside a dark pocket. */
    val PrimaryLight = Color(0xFF87D68D)

    /** Text on [PrimaryLight]; white would not be readable there. */
    val OnPrimaryLight = Color(0xFF0D2E1D)

    /** Selected container: nav pill, chosen treatment, chosen therapist. */
    val Celadon = Color(0xFFBCEBCB)

    /** The one darkened "pocket" per screen that holds the primary action. */
    val Pocket = Color(0xFF123B26)
    val OnPocket = Color(0xFFF7FFF6)
    val OnPocketMuted = Color(0xFFD7F2DC)

    /** Dusk lavender: the text colour of this palette, not a green-black. */
    val TextPrimary = Color(0xFF364355)
    val TextSecondary = Color(0xFF5F6C7E)

    val FieldBorder = Color(0xFFD5E6D1)
    val ButtonBorder = Color(0xFFD7EBD3)

    val Error = Color(0xFFB3261E)
    val ErrorContainer = Color(0xFFFBE3E1)
    val OnErrorContainer = Color(0xFF8C1D18)

    val Success = Color(0xFF117034)

    /** Tonal surface levels between [Background] and [Celadon]. */
    val Tone1 = Color(0xFFE2F0DF)
    val Tone2 = Color(0xFFD7EBD3)

    /** Recessed linen felt: time-slot trays and inline edit forms. */
    val Linen = Color(0xFFE4EBDC)

    /** Discounts stay inside the moss family rather than taking a colour of their own. */
    val Promo = Color(0xFF0E5A2A)
    val PromoContainer = Color(0xFFD7EBD3)

    val Warning = Color(0xFF9A5B00)
    val WarningContainer = Color(0xFFFBEFD9)

    val HeroGradientMid = Color(0xFF87D68D)
    val CardGradientEnd = Color(0xFFE2F0DF)
    val Shadow = Color(0x1A364355)
}
