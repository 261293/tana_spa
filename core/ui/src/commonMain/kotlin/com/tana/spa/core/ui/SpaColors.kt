package com.tana.spa.core.ui

import androidx.compose.ui.graphics.Color

/**
 * The limewash palette (ADR 0008): warm paper and card stock, umber ink, and one slate signal that
 * marks the primary action, the current selection and focus. Values are the OKLCH tokens in
 * `DESIGN.md` converted to sRGB.
 */
object SpaColors {
    val Paper = Color(0xFFF1ECE6)
    val Paper2 = Color(0xFFE9E3DB)
    val Paper3 = Color(0xFFDED6CD)
    val Card = Color(0xFFF9F7F2)

    val Ink = Color(0xFF1C110A)
    val InkSoft = Color(0xFF382E28)
    val Muted = Color(0xFF615953)

    val Rule = Color(0xFFC6C0B9)
    val Outline = Color(0xFF807972)

    val Slate = Color(0xFF264D71)
    val OnSlate = Color(0xFFF5F1EC)
    val SlateTint = Color(0xFFD3E2F0)
    val SlateDeep = Color(0xFF0E304D)
    val Focus = Color(0xFF326797)

    val Umber = Color(0xFF564335)
    val UmberTint = Color(0xFFE6DCD2)

    val Ochre = Color(0xFF8D5D1C)
    val OchreTint = Color(0xFFF6E8D0)
    val Moss = Color(0xFF3A6343)

    val Danger = Color(0xFF963828)
    val DangerTint = Color(0xFFFBE1DC)
    val DangerDeep = Color(0xFF6B2013)

    val Shadow = Color(0x291C110A)
}
