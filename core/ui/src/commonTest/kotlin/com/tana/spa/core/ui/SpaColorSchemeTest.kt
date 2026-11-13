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
        assertEquals(SpaColors.Eucalyptus, SpaColorScheme.primary)
        assertEquals(SpaColors.OnEucalyptus, SpaColorScheme.onPrimary)
        assertEquals(SpaColors.EucalyptusTint, SpaColorScheme.primaryContainer)
        assertEquals(SpaColors.EucalyptusDeep, SpaColorScheme.onPrimaryContainer)
        assertEquals(SpaColors.Umber, SpaColorScheme.secondary)
        assertEquals(SpaColors.UmberTint, SpaColorScheme.secondaryContainer)
        assertEquals(SpaColors.Ink, SpaColorScheme.onSecondaryContainer)
        assertEquals(SpaColors.Umber, SpaColorScheme.tertiary)
        assertEquals(SpaColors.UmberTint, SpaColorScheme.tertiaryContainer)
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
        assertEquals(SpaColors.UmberTint, SpaColorScheme.surfaceContainerHighest)
        assertEquals(SpaColors.Outline, SpaColorScheme.outline)
        assertEquals(SpaColors.Rule, SpaColorScheme.outlineVariant)
        assertEquals(SpaColors.Danger, SpaColorScheme.error)
        assertEquals(SpaColors.DangerTint, SpaColorScheme.errorContainer)
        assertEquals(SpaColors.DangerDeep, SpaColorScheme.onErrorContainer)
    }

    @Test
    fun extra_colors_match_design_tokens() {
        assertEquals(SpaColors.Eucalyptus, LightExtraColors.link)
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
            SpaColors.OnEucalyptus to SpaColors.Eucalyptus,
            SpaColors.Eucalyptus to SpaColors.Paper,
            SpaColors.Eucalyptus to SpaColors.Card,
            SpaColors.EucalyptusDeep to SpaColors.EucalyptusTint,
            SpaColors.Ink to SpaColors.Paper,
            SpaColors.Ink to SpaColors.Card,
            SpaColors.Ink to SpaColors.UmberTint,
            SpaColors.InkSoft to SpaColors.Paper,
            SpaColors.InkSoft to SpaColors.Paper2,
            SpaColors.InkSoft to SpaColors.Paper3,
            SpaColors.Muted to SpaColors.Paper,
            SpaColors.Muted to SpaColors.Card,
            SpaColors.Muted to SpaColors.Paper2,
            SpaColors.Umber to SpaColors.Card,
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
        assertTrue(contrastRatio(SpaColors.Focus, SpaColors.Paper) >= 3.0)
    }
}
