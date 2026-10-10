package com.tana.spa.core.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * The text styles the design file specifies, named so a screen never states a raw `sp` size.
 *
 * Each style is derived from the same base the call site used to inherit from, because `Text`
 * merges its ambient style: the button labels come from `labelLarge` (Material's `Button` provides
 * it), the field styles are bare because the field components built their own `TextStyle`, and
 * everything else comes from `bodyLarge`. Deriving from the wrong base silently changes
 * letterSpacing and lineHeight.
 */
@Immutable
data class SpaTextStyles(
    val brandTitle: TextStyle,
    val screenTitle: TextStyle,
    val prompt: TextStyle,
    val sectionLead: TextStyle,
    val body: TextStyle,
    val bodyRelaxed: TextStyle,
    val bodyLoose: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
    val overline: TextStyle,
    val buttonLabel: TextStyle,
    val fieldText: TextStyle,
    val fieldError: TextStyle,
    val otpDigit: TextStyle,
    val chatInput: TextStyle,
)

internal fun spaTextStyles(typography: Typography): SpaTextStyles {
    val base = typography.bodyLarge
    val bare = TextStyle(fontFamily = base.fontFamily)
    return SpaTextStyles(
        brandTitle = base.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        screenTitle = base.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        prompt = base.copy(fontSize = 16.sp),
        sectionLead = base.copy(fontSize = 15.sp),
        body = base.copy(fontSize = 14.sp),
        bodyRelaxed = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodyLoose = base.copy(fontSize = 14.sp, lineHeight = 22.sp),
        caption = base.copy(fontSize = 13.sp),
        label = base.copy(fontSize = 12.sp),
        overline = base.copy(fontSize = 11.sp),
        buttonLabel = typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        fieldText = bare.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
        fieldError = bare.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal),
        otpDigit = bare.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        chatInput = bare.copy(fontSize = 15.sp),
    )
}

internal val LocalSpaTextStyles = staticCompositionLocalOf { spaTextStyles(Typography()) }

object SpaTheme {
    val extraColors: SpaExtraColors
        @Composable @ReadOnlyComposable
        get() = LocalSpaExtraColors.current

    val textStyles: SpaTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalSpaTextStyles.current
}
