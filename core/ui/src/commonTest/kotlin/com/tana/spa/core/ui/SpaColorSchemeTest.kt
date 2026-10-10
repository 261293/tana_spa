package com.tana.spa.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Screens read colours through `MaterialTheme.colorScheme` and [SpaTheme], never [SpaColors]
 * directly. These assertions pin each role to the design-file token it was mapped from, so a
 * change to the scheme cannot silently repaint a screen that no longer names the token.
 */
class SpaColorSchemeTest {
    @Test
    fun scheme_roles_match_design_tokens() {
        assertEquals(SpaColors.Primary, SpaColorScheme.primary)
        assertEquals(SpaColors.OnPrimary, SpaColorScheme.onPrimary)
        assertEquals(SpaColors.Background, SpaColorScheme.background)
        assertEquals(SpaColors.TextPrimary, SpaColorScheme.onBackground)
        assertEquals(SpaColors.Surface, SpaColorScheme.surface)
        assertEquals(SpaColors.TextPrimary, SpaColorScheme.onSurface)
        assertEquals(SpaColors.TextSecondary, SpaColorScheme.onSurfaceVariant)
        assertEquals(SpaColors.FieldBorder, SpaColorScheme.outline)
        assertEquals(SpaColors.ButtonBorder, SpaColorScheme.outlineVariant)
        assertEquals(SpaColors.Error, SpaColorScheme.error)
    }

    @Test
    fun extra_colors_match_design_tokens() {
        assertEquals(SpaColors.Link, LightExtraColors.link)
        assertEquals(SpaColors.Success, LightExtraColors.success)
        assertEquals(SpaColors.Shadow, LightExtraColors.shadow)
        assertEquals(
            listOf(SpaColors.Primary, SpaColors.HeroGradientMid, SpaColors.Surface),
            LightExtraColors.heroGradient,
        )
        assertEquals(listOf(SpaColors.Background, SpaColors.CardGradientEnd), LightExtraColors.cardGradient)
    }
}
