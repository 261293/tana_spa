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
    val shadow: Color,
    val heroGradient: List<Color>,
    val cardGradient: List<Color>,
)

internal val LightExtraColors = SpaExtraColors(
    link = SpaColors.Link,
    success = SpaColors.Success,
    shadow = SpaColors.Shadow,
    heroGradient = listOf(SpaColors.Primary, SpaColors.HeroGradientMid, SpaColors.Surface),
    cardGradient = listOf(SpaColors.Background, SpaColors.CardGradientEnd),
)

internal val LocalSpaExtraColors = staticCompositionLocalOf { LightExtraColors }
