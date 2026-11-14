package com.tana.spa.core.ui

import androidx.compose.ui.graphics.Color

/**
 * The mineral-spring palette (ADR 0008, amended 2026-11-13): a cool mist ground with a sage field
 * between sections, slate ink, and one copper signal for the primary action, the current selection
 * and focus. Eucalyptus stays as the quiet second colour.
 */
object SpaColors {
    val Paper = Color(0xFFF2F5F4)
    val Paper2 = Color(0xFFE3EAE8)
    val Paper3 = Color(0xFFD5DFDC)
    val Card = Color(0xFFFFFFFF)

    val Ink = Color(0xFF16282B)
    val InkSoft = Color(0xFF2C3E41)
    val Muted = Color(0xFF4A5A5D)

    val Rule = Color(0xFFC9D3D1)
    val Outline = Color(0xFF6F7F82)

    val Copper = Color(0xFF9A521F)
    val OnCopper = Color(0xFFFFFFFF)
    val CopperTint = Color(0xFFF7E4D2)
    val CopperDeep = Color(0xFF3D1F08)

    /** Copper lifted for type set over a darkened photograph, where the deep copper would sink. */
    val CopperGlow = Color(0xFFE9A66C)

    val Eucalyptus = Color(0xFF2E5B55)
    val OnEucalyptus = Color(0xFFF5F1EC)
    val EucalyptusTint = Color(0xFFD4E5DF)
    val EucalyptusDeep = Color(0xFF0F3A35)

    val Slate = Color(0xFF3C4F52)
    val SlateTint = Color(0xFFCCD7D4)

    val Ochre = Color(0xFF8D5D1C)
    val OchreTint = Color(0xFFF6E8D0)
    val Moss = Color(0xFF3A6343)

    val Danger = Color(0xFF963828)
    val DangerTint = Color(0xFFFBE1DC)
    val DangerDeep = Color(0xFF6B2013)

    val Shadow = Color(0x2916282B)
}
