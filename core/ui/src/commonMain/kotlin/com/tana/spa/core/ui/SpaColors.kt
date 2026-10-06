package com.tana.spa.core.ui

import androidx.compose.ui.graphics.Color

/** Design tokens taken from the Figma file ("SPA BOOKING APP"). */
object SpaColors {
    val Background = Color(0xFFF4FBFF)
    val Surface = Color(0xFFFCFEFF)
    val Primary = Color(0xFFFE5578)
    val OnPrimary = Color.White
    /** Pink used for inline links ("Đăng kí ngay"). */
    val Link = Color(0xFFFF5592)
    val TextPrimary = Color.Black
    /** Placeholder, icon and secondary-button text colour. */
    val TextSecondary = Color(0xFF797979)
    val FieldBorder = Color(0xFFD1D1D1)
    val ButtonBorder = Color(0xFFE9E9E9)
    // Not in the design file; a conventional accessible red for validation messages.
    val Error = Color(0xFFD32F2F)
}
