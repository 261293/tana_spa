package com.tana.spa.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design-file colours that Material 3 has no scheme role for. Read them through [SpaTheme] so a
 * screen never reaches for a raw [SpaColors] constant, which would survive a theme change.
 */
@Immutable
data class SpaExtraColors(
    val link: Color,
    val success: Color,
    val warning: Color,
    val warningContainer: Color,
    /** The dark forest panel the design closes a page with, and the colours legible inside it. */
    val pocket: Color,
    val onPocket: Color,
    val onPocketMuted: Color,
    val pocketAction: Color,
    val onPocketAction: Color,
    /** The recessed tray behind time slots and inline edit forms. */
    val felt: Color,
    val shadow: Color,
    val tag: Color,
    val inkSoft: Color,
    /** Type set over a darkened photograph: the words and the one accent among them. */
    val onPhoto: Color,
    val onPhotoAccent: Color,
)

internal val LightExtraColors = SpaExtraColors(
    link = SpaColors.Copper,
    success = SpaColors.Moss,
    warning = SpaColors.Ochre,
    warningContainer = SpaColors.OchreTint,
    pocket = SpaColors.Ink,
    onPocket = SpaColors.Paper,
    onPocketMuted = SpaColors.Paper3,
    pocketAction = SpaColors.Card,
    onPocketAction = SpaColors.Ink,
    felt = SpaColors.Paper2,
    shadow = SpaColors.Shadow,
    tag = SpaColors.Muted,
    inkSoft = SpaColors.InkSoft,
    onPhoto = SpaColors.Card,
    onPhotoAccent = SpaColors.CopperGlow,
)

internal val LocalSpaExtraColors = staticCompositionLocalOf { LightExtraColors }
