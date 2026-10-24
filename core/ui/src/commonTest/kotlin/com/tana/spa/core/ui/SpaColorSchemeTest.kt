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
        assertEquals(SpaColors.Forest, SpaColorScheme.primary)
        assertEquals(SpaColors.OnForest, SpaColorScheme.onPrimary)
        assertEquals(SpaColors.SageTint, SpaColorScheme.primaryContainer)
        assertEquals(SpaColors.Forest, SpaColorScheme.onPrimaryContainer)
        assertEquals(SpaColors.Forest600, SpaColorScheme.secondary)
        assertEquals(SpaColors.SageSoft, SpaColorScheme.secondaryContainer)
        assertEquals(SpaColors.Clay, SpaColorScheme.tertiary)
        assertEquals(SpaColors.ClaySoft, SpaColorScheme.tertiaryContainer)
        assertEquals(SpaColors.Ground, SpaColorScheme.background)
        assertEquals(SpaColors.Ink, SpaColorScheme.onBackground)
        assertEquals(SpaColors.Surface, SpaColorScheme.surface)
        assertEquals(SpaColors.Ink, SpaColorScheme.onSurface)
        assertEquals(SpaColors.SurfaceAlt, SpaColorScheme.surfaceVariant)
        assertEquals(SpaColors.Muted, SpaColorScheme.onSurfaceVariant)
        assertEquals(SpaColors.Surface, SpaColorScheme.surfaceContainerLowest)
        assertEquals(SpaColors.Ground, SpaColorScheme.surfaceContainerLow)
        assertEquals(SpaColors.SurfaceAlt, SpaColorScheme.surfaceContainer)
        assertEquals(SpaColors.SageSoft, SpaColorScheme.surfaceContainerHigh)
        assertEquals(SpaColors.SageTint, SpaColorScheme.surfaceContainerHighest)
        assertEquals(SpaColors.LineStrong, SpaColorScheme.outline)
        assertEquals(SpaColors.Line, SpaColorScheme.outlineVariant)
        assertEquals(SpaColors.Danger, SpaColorScheme.error)
        assertEquals(SpaColors.ClaySoft, SpaColorScheme.errorContainer)
    }

    @Test
    fun extra_colors_match_design_tokens() {
        assertEquals(SpaColors.Forest600, LightExtraColors.link)
        assertEquals(SpaColors.Forest600, LightExtraColors.success)
        assertEquals(SpaColors.Clay, LightExtraColors.warning)
        assertEquals(SpaColors.ClaySoft, LightExtraColors.warningContainer)
        assertEquals(SpaColors.ForestDeep, LightExtraColors.pocket)
        assertEquals(SpaColors.OnForest, LightExtraColors.onPocket)
        assertEquals(SpaColors.OnForestMuted, LightExtraColors.onPocketMuted)
        assertEquals(SpaColors.OnForest, LightExtraColors.pocketAction)
        assertEquals(SpaColors.ForestDeep, LightExtraColors.onPocketAction)
        assertEquals(SpaColors.SurfaceAlt, LightExtraColors.felt)
        assertEquals(SpaColors.Shadow, LightExtraColors.shadow)
        assertEquals(SpaColors.Moss, LightExtraColors.eyebrow)
        assertEquals(listOf(SpaColors.Forest, SpaColors.ForestDeep), LightExtraColors.heroGradient)
        assertEquals(listOf(SpaColors.Surface, SpaColors.SurfaceAlt), LightExtraColors.cardGradient)
    }

    /** WCAG 2.2 1.4.3 for every pairing the screens actually use. */
    @Test
    fun text_pairs_are_readable() {
        assertTrue(contrastRatio(SpaColors.OnForest, SpaColors.Forest) >= 4.5)
        assertTrue(contrastRatio(SpaColors.Ink, SpaColors.Ground) >= 4.5)
        assertTrue(contrastRatio(SpaColors.Ink, SpaColors.Surface) >= 4.5)
        assertTrue(contrastRatio(SpaColors.Muted, SpaColors.Ground) >= 4.5)
        assertTrue(contrastRatio(SpaColors.Muted, SpaColors.Surface) >= 4.5)
        assertTrue(contrastRatio(SpaColors.Forest, SpaColors.Ground) >= 4.5)
        assertTrue(contrastRatio(SpaColors.Forest, SpaColors.SageTint) >= 4.5)
        assertTrue(contrastRatio(SpaColors.Moss, SpaColors.Ground) >= 4.5)
        assertTrue(contrastRatio(SpaColors.Forest600, SpaColors.Surface) >= 4.5)
        assertTrue(contrastRatio(SpaColors.OnForest, SpaColors.ForestDeep) >= 4.5)
        assertTrue(contrastRatio(SpaColors.OnForestMuted, SpaColors.ForestDeep) >= 4.5)
        assertTrue(contrastRatio(SpaColors.ForestDeep, SpaColors.OnForest) >= 4.5)
        assertTrue(contrastRatio(SpaColors.DangerDeep, SpaColors.ClaySoft) >= 4.5)
        assertTrue(contrastRatio(SpaColors.Danger, SpaColors.Surface) >= 4.5)
    }
}
