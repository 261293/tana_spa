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
    /** The uppercase label that sits above a heading. */
    val eyebrow: Color,
    val heroGradient: List<Color>,
    val cardGradient: List<Color>,
)

internal val LightExtraColors = SpaExtraColors(
    link = SpaColors.Forest600,
    success = SpaColors.Forest600,
    warning = SpaColors.Clay,
    warningContainer = SpaColors.ClaySoft,
    pocket = SpaColors.ForestDeep,
    onPocket = SpaColors.OnForest,
    onPocketMuted = SpaColors.OnForestMuted,
    pocketAction = SpaColors.OnForest,
    onPocketAction = SpaColors.ForestDeep,
    felt = SpaColors.SurfaceAlt,
    shadow = SpaColors.Shadow,
    eyebrow = SpaColors.Moss,
    heroGradient = listOf(SpaColors.Forest, SpaColors.ForestDeep),
    cardGradient = listOf(SpaColors.Surface, SpaColors.SurfaceAlt),
)

internal val LocalSpaExtraColors = staticCompositionLocalOf { LightExtraColors }
