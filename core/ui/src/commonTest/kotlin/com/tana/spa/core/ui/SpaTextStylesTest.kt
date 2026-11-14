package com.tana.spa.core.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class SpaTextStylesTest {
    private val typography = Typography()
    private val display = FontFamily.Serif
    private val styles = spaTextStyles(typography, display)

    @Test
    fun sizes_match_the_design_tokens() {
        assertEquals(24.sp, styles.brandTitle.fontSize)
        assertEquals(36.sp, styles.displayTitle.fontSize)
        assertEquals(28.sp, styles.screenTitle.fontSize)
        assertEquals(44.sp, styles.heroTitle.fontSize)
        assertEquals(20.sp, styles.cardTitle.fontSize)
        assertEquals(17.sp, styles.prompt.fontSize)
        assertEquals(16.sp, styles.sectionLead.fontSize)
        assertEquals(15.sp, styles.chatInput.fontSize)
        assertEquals(16.sp, styles.body.fontSize)
        assertEquals(16.sp, styles.bodyRelaxed.fontSize)
        assertEquals(16.sp, styles.bodyLoose.fontSize)
        assertEquals(13.sp, styles.caption.fontSize)
        assertEquals(12.sp, styles.label.fontSize)
        assertEquals(13.sp, styles.tag.fontSize)
        assertEquals(15.sp, styles.buttonLabel.fontSize)
        assertEquals(15.sp, styles.fieldText.fontSize)
        assertEquals(13.sp, styles.fieldError.fontSize)
        assertEquals(22.sp, styles.otpDigit.fontSize)
    }

    @Test
    fun weights_match_the_design_tokens() {
        assertEquals(FontWeight.Medium, styles.brandTitle.fontWeight)
        assertEquals(FontWeight.Medium, styles.screenTitle.fontWeight)
        assertEquals(FontWeight.Medium, styles.displayTitle.fontWeight)
        assertEquals(FontWeight.Medium, styles.heroTitle.fontWeight)
        assertEquals(FontWeight.Normal, styles.cardTitle.fontWeight)
        assertEquals(FontWeight.Medium, styles.tag.fontWeight)
        assertEquals(FontWeight.SemiBold, styles.buttonLabel.fontWeight)
        assertEquals(FontWeight.SemiBold, styles.otpDigit.fontWeight)
        assertEquals(FontWeight.Normal, styles.fieldText.fontWeight)
    }

    @Test
    fun explicit_line_heights_are_kept() {
        assertEquals(40.sp, styles.displayTitle.lineHeight)
        assertEquals(32.sp, styles.screenTitle.lineHeight)
        assertEquals(48.sp, styles.heroTitle.lineHeight)
        assertEquals(26.sp, styles.cardTitle.lineHeight)
        assertEquals(24.sp, styles.bodyRelaxed.lineHeight)
        assertEquals(26.sp, styles.bodyLoose.lineHeight)
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

    /** Headings are the display serif; everything else stays on the body face. */
    @Test
    fun headings_use_the_display_face_and_the_rest_stays_on_the_body_face() {
        val headings = with(styles) { listOf(brandTitle, heroTitle, displayTitle, screenTitle, cardTitle) }
        headings.forEach { assertEquals(display, it.fontFamily) }

        val body = typography.bodyLarge.fontFamily
        val rest = with(styles) {
            listOf(
                prompt, sectionLead, this.body, bodyRelaxed, bodyLoose, caption, label, tag,
                buttonLabel, fieldText, fieldError, otpDigit, chatInput,
            )
        }
        rest.forEach { assertEquals(body, it.fontFamily) }
    }
}
