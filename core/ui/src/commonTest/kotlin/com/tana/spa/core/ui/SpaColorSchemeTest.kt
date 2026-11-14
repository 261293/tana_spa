package com.tana.spa.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Screens read colours through `MaterialTheme.colorScheme` and [SpaTheme], never [SpaColors]
 * directly. These assertions pin each role to the design token it was mapped from, so a change to
 * the scheme cannot silently repaint a screen that no longer names the token.
 */
class SpaColorSchemeTest {
    @Test
    fun scheme_roles_match_design_tokens() {
        assertEquals(SpaColors.Copper, SpaColorScheme.primary)
        assertEquals(SpaColors.OnCopper, SpaColorScheme.onPrimary)
        assertEquals(SpaColors.CopperTint, SpaColorScheme.primaryContainer)
        assertEquals(SpaColors.CopperDeep, SpaColorScheme.onPrimaryContainer)
        assertEquals(SpaColors.Eucalyptus, SpaColorScheme.secondary)
        assertEquals(SpaColors.EucalyptusTint, SpaColorScheme.secondaryContainer)
        assertEquals(SpaColors.EucalyptusDeep, SpaColorScheme.onSecondaryContainer)
        assertEquals(SpaColors.Slate, SpaColorScheme.tertiary)
        assertEquals(SpaColors.SlateTint, SpaColorScheme.tertiaryContainer)
        assertEquals(SpaColors.Paper, SpaColorScheme.background)
        assertEquals(SpaColors.Ink, SpaColorScheme.onBackground)
        assertEquals(SpaColors.Card, SpaColorScheme.surface)
        assertEquals(SpaColors.Ink, SpaColorScheme.onSurface)
        assertEquals(SpaColors.Paper2, SpaColorScheme.surfaceVariant)
        assertEquals(SpaColors.Muted, SpaColorScheme.onSurfaceVariant)
        assertEquals(SpaColors.Card, SpaColorScheme.surfaceContainerLowest)
        assertEquals(SpaColors.Paper, SpaColorScheme.surfaceContainerLow)
        assertEquals(SpaColors.Paper2, SpaColorScheme.surfaceContainer)
        assertEquals(SpaColors.Paper3, SpaColorScheme.surfaceContainerHigh)
        assertEquals(SpaColors.SlateTint, SpaColorScheme.surfaceContainerHighest)
        assertEquals(SpaColors.Outline, SpaColorScheme.outline)
        assertEquals(SpaColors.Rule, SpaColorScheme.outlineVariant)
        assertEquals(SpaColors.Danger, SpaColorScheme.error)
        assertEquals(SpaColors.DangerTint, SpaColorScheme.errorContainer)
        assertEquals(SpaColors.DangerDeep, SpaColorScheme.onErrorContainer)
    }

    @Test
    fun extra_colors_match_design_tokens() {
        assertEquals(SpaColors.Copper, LightExtraColors.link)
        assertEquals(SpaColors.Moss, LightExtraColors.success)
        assertEquals(SpaColors.Ochre, LightExtraColors.warning)
        assertEquals(SpaColors.OchreTint, LightExtraColors.warningContainer)
        assertEquals(SpaColors.Ink, LightExtraColors.pocket)
        assertEquals(SpaColors.Paper, LightExtraColors.onPocket)
        assertEquals(SpaColors.Paper3, LightExtraColors.onPocketMuted)
        assertEquals(SpaColors.Card, LightExtraColors.pocketAction)
        assertEquals(SpaColors.Ink, LightExtraColors.onPocketAction)
        assertEquals(SpaColors.Paper2, LightExtraColors.felt)
        assertEquals(SpaColors.Shadow, LightExtraColors.shadow)
        assertEquals(SpaColors.Muted, LightExtraColors.tag)
        assertEquals(SpaColors.InkSoft, LightExtraColors.inkSoft)
    }

    /** WCAG 2.2 1.4.3 for every pairing the screens actually use. */
    @Test
    fun text_pairs_are_readable() {
        val pairs = listOf(
            SpaColors.OnCopper to SpaColors.Copper,
            SpaColors.Copper to SpaColors.Paper,
            SpaColors.Copper to SpaColors.Paper2,
            SpaColors.CopperDeep to SpaColors.CopperTint,
            SpaColors.OnEucalyptus to SpaColors.Eucalyptus,
            SpaColors.Eucalyptus to SpaColors.Paper,
            SpaColors.Eucalyptus to SpaColors.Card,
            SpaColors.EucalyptusDeep to SpaColors.EucalyptusTint,
            SpaColors.Ink to SpaColors.Paper,
            SpaColors.Ink to SpaColors.Card,
            SpaColors.Ink to SpaColors.SlateTint,
            SpaColors.InkSoft to SpaColors.Paper,
            SpaColors.InkSoft to SpaColors.Paper2,
            SpaColors.InkSoft to SpaColors.Paper3,
            SpaColors.Muted to SpaColors.Paper,
            SpaColors.Muted to SpaColors.Card,
            SpaColors.Muted to SpaColors.Paper2,
            SpaColors.Slate to SpaColors.Card,
            SpaColors.Ochre to SpaColors.Card,
            SpaColors.Ochre to SpaColors.OchreTint,
            SpaColors.Moss to SpaColors.Card,
            SpaColors.Danger to SpaColors.Card,
            SpaColors.DangerDeep to SpaColors.DangerTint,
            SpaColors.Paper to SpaColors.Ink,
            SpaColors.Paper3 to SpaColors.Ink,
            SpaColors.Ink to SpaColors.Card,
        )
        for ((text, ground) in pairs) assertTrue(contrastRatio(text, ground) >= 4.5, "$text on $ground")
    }

    @Test
    fun a_control_boundary_can_be_seen() {
        assertTrue(contrastRatio(SpaColors.Outline, SpaColors.Card) >= 3.0)
        assertTrue(contrastRatio(SpaColors.Outline, SpaColors.Paper) >= 3.0)
        assertTrue(contrastRatio(SpaColors.Copper, SpaColors.Paper) >= 3.0)
    }
}
