package com.tana.spa.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpaColorSchemeTest {
    @Test
    fun scheme_roles_match_design_tokens() {
        assertEquals(SpaColors.Basalt, SpaColorScheme.primary)
        assertEquals(SpaColors.OnBasalt, SpaColorScheme.onPrimary)
        assertEquals(SpaColors.JadeTint, SpaColorScheme.primaryContainer)
        assertEquals(SpaColors.JadeDeep, SpaColorScheme.onPrimaryContainer)
        assertEquals(SpaColors.Jade, SpaColorScheme.secondary)
        assertEquals(SpaColors.OnJade, SpaColorScheme.onSecondary)
        assertEquals(SpaColors.JadeTint, SpaColorScheme.secondaryContainer)
        assertEquals(SpaColors.JadeDeep, SpaColorScheme.onSecondaryContainer)
        assertEquals(SpaColors.Muted, SpaColorScheme.tertiary)
        assertEquals(SpaColors.Field, SpaColorScheme.tertiaryContainer)
        assertEquals(SpaColors.Ground, SpaColorScheme.background)
        assertEquals(SpaColors.Ink, SpaColorScheme.onBackground)
        assertEquals(SpaColors.Panel, SpaColorScheme.surface)
        assertEquals(SpaColors.Ink, SpaColorScheme.onSurface)
        assertEquals(SpaColors.Field, SpaColorScheme.surfaceVariant)
        assertEquals(SpaColors.Muted, SpaColorScheme.onSurfaceVariant)
        assertEquals(SpaColors.Panel, SpaColorScheme.surfaceContainerLowest)
        assertEquals(SpaColors.Ground, SpaColorScheme.surfaceContainerLow)
        assertEquals(SpaColors.Field, SpaColorScheme.surfaceContainer)
        assertEquals(SpaColors.FieldHigh, SpaColorScheme.surfaceContainerHigh)
        assertEquals(SpaColors.FieldHighest, SpaColorScheme.surfaceContainerHighest)
        assertEquals(SpaColors.Ink, SpaColorScheme.inverseSurface)
        assertEquals(SpaColors.Outline, SpaColorScheme.outline)
        assertEquals(SpaColors.Rule, SpaColorScheme.outlineVariant)
        assertEquals(SpaColors.Danger, SpaColorScheme.error)
        assertEquals(SpaColors.DangerTint, SpaColorScheme.errorContainer)
        assertEquals(SpaColors.DangerDeep, SpaColorScheme.onErrorContainer)
    }

    @Test
    fun extra_colors_match_design_tokens() {
        assertEquals(SpaColors.Jade, LightExtraColors.link)
        assertEquals(SpaColors.Moss, LightExtraColors.success)
        assertEquals(SpaColors.Ochre, LightExtraColors.warning)
        assertEquals(SpaColors.OchreTint, LightExtraColors.warningContainer)
        assertEquals(SpaColors.Ink, LightExtraColors.pocket)
        assertEquals(SpaColors.Ground, LightExtraColors.onPocket)
        assertEquals(SpaColors.FieldHighest, LightExtraColors.onPocketMuted)
        assertEquals(SpaColors.Field, LightExtraColors.felt)
        assertEquals(SpaColors.Shadow, LightExtraColors.shadow)
        assertEquals(SpaColors.Muted, LightExtraColors.tag)
        assertEquals(SpaColors.InkSoft, LightExtraColors.inkSoft)
        assertEquals(SpaColors.Panel, LightExtraColors.onPhoto)
    }

    @Test
    fun text_pairs_are_readable() {
        val pairs = listOf(
            SpaColors.OnBasalt to SpaColors.Basalt,
            SpaColors.Basalt to SpaColors.Ground,
            SpaColors.OnJade to SpaColors.Jade,
            SpaColors.Jade to SpaColors.Ground,
            SpaColors.Jade to SpaColors.Panel,
            SpaColors.Jade to SpaColors.Field,
            SpaColors.JadeDeep to SpaColors.JadeTint,
            SpaColors.Ink to SpaColors.Ground,
            SpaColors.Ink to SpaColors.Panel,
            SpaColors.Ink to SpaColors.Field,
            SpaColors.Ink to SpaColors.FieldHighest,
            SpaColors.InkSoft to SpaColors.Ground,
            SpaColors.InkSoft to SpaColors.Field,
            SpaColors.Muted to SpaColors.Ground,
            SpaColors.Muted to SpaColors.Panel,
            SpaColors.Muted to SpaColors.Field,
            SpaColors.Ochre to SpaColors.Panel,
            SpaColors.Ochre to SpaColors.OchreTint,
            SpaColors.Moss to SpaColors.Panel,
            SpaColors.Danger to SpaColors.Panel,
            SpaColors.DangerDeep to SpaColors.DangerTint,
            SpaColors.Ground to SpaColors.Ink,
            SpaColors.FieldHighest to SpaColors.Ink,
        )
        for ((text, ground) in pairs) assertTrue(contrastRatio(text, ground) >= 4.5, "$text on $ground")
    }

    @Test
    fun a_control_boundary_can_be_seen() {
        assertTrue(contrastRatio(SpaColors.Outline, SpaColors.Panel) >= 3.0)
        assertTrue(contrastRatio(SpaColors.Outline, SpaColors.Ground) >= 3.0)
        assertTrue(contrastRatio(SpaColors.Jade, SpaColors.Ground) >= 3.0)
    }
}
