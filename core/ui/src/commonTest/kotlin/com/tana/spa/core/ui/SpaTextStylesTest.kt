package com.tana.spa.core.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class SpaTextStylesTest {
    private val typography = Typography()
    private val styles = spaTextStyles(typography)

    @Test
    fun sizes_match_the_design_file() {
        assertEquals(24.sp, styles.brandTitle.fontSize)
        assertEquals(22.sp, styles.screenTitle.fontSize)
        assertEquals(16.sp, styles.prompt.fontSize)
        assertEquals(15.sp, styles.sectionLead.fontSize)
        assertEquals(15.sp, styles.chatInput.fontSize)
        assertEquals(14.sp, styles.body.fontSize)
        assertEquals(14.sp, styles.bodyRelaxed.fontSize)
        assertEquals(14.sp, styles.bodyLoose.fontSize)
        assertEquals(13.sp, styles.caption.fontSize)
        assertEquals(12.sp, styles.label.fontSize)
        assertEquals(11.sp, styles.overline.fontSize)
        assertEquals(16.sp, styles.buttonLabel.fontSize)
        assertEquals(16.sp, styles.fieldText.fontSize)
        assertEquals(13.sp, styles.fieldError.fontSize)
        assertEquals(22.sp, styles.otpDigit.fontSize)
    }

    @Test
    fun weights_match_the_design_file() {
        assertEquals(FontWeight.Bold, styles.brandTitle.fontWeight)
        assertEquals(FontWeight.Bold, styles.screenTitle.fontWeight)
        assertEquals(FontWeight.SemiBold, styles.buttonLabel.fontWeight)
        assertEquals(FontWeight.SemiBold, styles.otpDigit.fontWeight)
        assertEquals(FontWeight.Normal, styles.fieldText.fontWeight)
    }

    @Test
    fun explicit_line_heights_are_kept() {
        assertEquals(20.sp, styles.bodyRelaxed.lineHeight)
        assertEquals(22.sp, styles.bodyLoose.lineHeight)
    }

    /**
     * `Text` merges its ambient style, so a token has to start from whatever the call site used to
     * inherit: `bodyLarge` for ordinary text, `labelLarge` inside a Material button, and nothing at
     * all for the fields, which built bare styles. Starting from the wrong base would shift
     * letterSpacing and lineHeight on screens that look untouched in the diff.
     */
    @Test
    fun tokens_keep_the_base_their_call_sites_inherited() {
        assertEquals(typography.bodyLarge.letterSpacing, styles.body.letterSpacing)
        assertEquals(typography.bodyLarge.lineHeight, styles.body.lineHeight)
        assertEquals(typography.labelLarge.letterSpacing, styles.buttonLabel.letterSpacing)
        assertEquals(typography.labelLarge.lineHeight, styles.buttonLabel.lineHeight)

        for (bare in listOf(styles.fieldText, styles.fieldError, styles.otpDigit, styles.chatInput)) {
            assertEquals(TextUnit.Unspecified, bare.lineHeight)
            assertEquals(TextUnit.Unspecified, bare.letterSpacing)
        }
    }

    @Test
    fun every_token_carries_the_theme_font() {
        val family = typography.bodyLarge.fontFamily
        val all = with(styles) {
            listOf(
                brandTitle, screenTitle, prompt, sectionLead, body, bodyRelaxed, bodyLoose,
                caption, label, overline, buttonLabel, fieldText, fieldError, otpDigit, chatInput,
            )
        }
        all.forEach { assertEquals(family, it.fontFamily) }
    }
}
