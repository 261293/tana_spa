package com.tana.spa.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertEquals(SpaColors.Celadon, SpaColorScheme.primaryContainer)
        assertEquals(SpaColors.Primary, SpaColorScheme.onPrimaryContainer)
        assertEquals(SpaColors.Promo, SpaColorScheme.secondary)
        assertEquals(SpaColors.PromoContainer, SpaColorScheme.secondaryContainer)
        assertEquals(SpaColors.Warning, SpaColorScheme.tertiary)
        assertEquals(SpaColors.WarningContainer, SpaColorScheme.tertiaryContainer)
        assertEquals(SpaColors.Background, SpaColorScheme.background)
        assertEquals(SpaColors.TextPrimary, SpaColorScheme.onBackground)
        assertEquals(SpaColors.Surface, SpaColorScheme.surface)
        assertEquals(SpaColors.TextPrimary, SpaColorScheme.onSurface)
        assertEquals(SpaColors.Tone1, SpaColorScheme.surfaceVariant)
        assertEquals(SpaColors.TextSecondary, SpaColorScheme.onSurfaceVariant)
        assertEquals(SpaColors.Background, SpaColorScheme.surfaceContainerLowest)
        assertEquals(SpaColors.Surface, SpaColorScheme.surfaceContainerLow)
        assertEquals(SpaColors.Tone1, SpaColorScheme.surfaceContainer)
        assertEquals(SpaColors.Tone2, SpaColorScheme.surfaceContainerHigh)
        assertEquals(SpaColors.Celadon, SpaColorScheme.surfaceContainerHighest)
        assertEquals(SpaColors.FieldBorder, SpaColorScheme.outline)
        assertEquals(SpaColors.ButtonBorder, SpaColorScheme.outlineVariant)
        assertEquals(SpaColors.Error, SpaColorScheme.error)
        assertEquals(SpaColors.ErrorContainer, SpaColorScheme.errorContainer)
    }

    @Test
    fun extra_colors_match_design_tokens() {
        assertEquals(SpaColors.Primary, LightExtraColors.link)
        assertEquals(SpaColors.Success, LightExtraColors.success)
        assertEquals(SpaColors.Warning, LightExtraColors.warning)
        assertEquals(SpaColors.WarningContainer, LightExtraColors.warningContainer)
        assertEquals(SpaColors.Pocket, LightExtraColors.pocket)
        assertEquals(SpaColors.OnPocket, LightExtraColors.onPocket)
        assertEquals(SpaColors.OnPocketMuted, LightExtraColors.onPocketMuted)
        assertEquals(SpaColors.PrimaryLight, LightExtraColors.pocketAction)
        assertEquals(SpaColors.OnPrimaryLight, LightExtraColors.onPocketAction)
        assertEquals(SpaColors.Linen, LightExtraColors.felt)
        assertEquals(SpaColors.Shadow, LightExtraColors.shadow)
        assertEquals(
            listOf(SpaColors.Primary, SpaColors.HeroGradientMid, SpaColors.Surface),
            LightExtraColors.heroGradient,
        )
        assertEquals(listOf(SpaColors.Background, SpaColors.CardGradientEnd), LightExtraColors.cardGradient)
    }

    /**
     * WCAG 2.2 1.4.3 for every pairing the screens actually use. The design file also offers a
     * paler "muted" lavender for captions; it lands near 3:1 on the page, so captions use
     * [SpaColors.TextSecondary] instead and that colour is pinned here.
     */
    @Test
    fun text_pairs_are_readable() {
        assertTrue(contrastRatio(SpaColors.OnPrimary, SpaColors.Primary) >= 4.5)
        assertTrue(contrastRatio(SpaColors.TextPrimary, SpaColors.Background) >= 4.5)
        assertTrue(contrastRatio(SpaColors.TextPrimary, SpaColors.Surface) >= 4.5)
        assertTrue(contrastRatio(SpaColors.TextSecondary, SpaColors.Background) >= 4.5)
        assertTrue(contrastRatio(SpaColors.TextSecondary, SpaColors.Surface) >= 4.5)
        assertTrue(contrastRatio(SpaColors.Primary, SpaColors.Background) >= 4.5)
        assertTrue(contrastRatio(SpaColors.Primary, SpaColors.Celadon) >= 4.5)
        assertTrue(contrastRatio(SpaColors.OnPocket, SpaColors.Pocket) >= 4.5)
        assertTrue(contrastRatio(SpaColors.OnPocketMuted, SpaColors.Pocket) >= 4.5)
        assertTrue(contrastRatio(SpaColors.OnPrimaryLight, SpaColors.PrimaryLight) >= 4.5)
    }
}
