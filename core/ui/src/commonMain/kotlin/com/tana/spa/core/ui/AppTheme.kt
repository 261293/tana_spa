package com.tana.spa.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.resources.Res
import com.tana.spa.core.ui.resources.hanken_grotesk_medium
import com.tana.spa.core.ui.resources.hanken_grotesk_regular
import com.tana.spa.core.ui.resources.hanken_grotesk_semibold
import com.tana.spa.core.ui.resources.newsreader_light
import com.tana.spa.core.ui.resources.newsreader_regular
import org.jetbrains.compose.resources.Font

@Composable
fun hankenGroteskFontFamily(): FontFamily = FontFamily(
    Font(Res.font.hanken_grotesk_regular, FontWeight.Normal),
    Font(Res.font.hanken_grotesk_medium, FontWeight.Medium),
    Font(Res.font.hanken_grotesk_semibold, FontWeight.SemiBold),
)

@Composable
fun newsreaderFontFamily(): FontFamily = FontFamily(
    Font(Res.font.newsreader_light, FontWeight.Light),
    Font(Res.font.newsreader_regular, FontWeight.Normal),
)

internal val SpaColorScheme = lightColorScheme(
    primary = SpaColors.Eucalyptus,
    onPrimary = SpaColors.OnEucalyptus,
    primaryContainer = SpaColors.EucalyptusTint,
    onPrimaryContainer = SpaColors.EucalyptusDeep,
    secondary = SpaColors.Umber,
    onSecondary = SpaColors.Card,
    secondaryContainer = SpaColors.UmberTint,
    onSecondaryContainer = SpaColors.Ink,
    tertiary = SpaColors.Umber,
    onTertiary = SpaColors.Card,
    tertiaryContainer = SpaColors.UmberTint,
    onTertiaryContainer = SpaColors.Ink,
    background = SpaColors.Paper,
    onBackground = SpaColors.Ink,
    surface = SpaColors.Card,
    onSurface = SpaColors.Ink,
    surfaceVariant = SpaColors.Paper2,
    onSurfaceVariant = SpaColors.Muted,
    surfaceTint = SpaColors.Eucalyptus,
    surfaceContainerLowest = SpaColors.Card,
    surfaceContainerLow = SpaColors.Paper,
    surfaceContainer = SpaColors.Paper2,
    surfaceContainerHigh = SpaColors.Paper3,
    surfaceContainerHighest = SpaColors.UmberTint,
    inverseSurface = SpaColors.Ink,
    inverseOnSurface = SpaColors.Paper,
    inversePrimary = SpaColors.EucalyptusTint,
    outline = SpaColors.Outline,
    outlineVariant = SpaColors.Rule,
    error = SpaColors.Danger,
    onError = SpaColors.Card,
    errorContainer = SpaColors.DangerTint,
    onErrorContainer = SpaColors.DangerDeep,
)

/** Smaller than Material's defaults: card stock is cut, not moulded. */
internal val SpaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val bodyFamily = hankenGroteskFontFamily()
    val displayFamily = newsreaderFontFamily()
    val typography = remember(bodyFamily, displayFamily) { Typography().withFamilies(bodyFamily, displayFamily) }
    val textStyles = remember(typography, displayFamily) { spaTextStyles(typography, displayFamily) }
    val motion = if (platformPrefersReducedMotion()) NoMotion else FullMotion
    CompositionLocalProvider(
        LocalSpaExtraColors provides LightExtraColors,
        LocalSpaTextStyles provides textStyles,
        LocalSpaMotion provides motion,
    ) {
        MaterialTheme(colorScheme = SpaColorScheme, typography = typography, shapes = SpaShapes, content = content)
    }
}

private fun Typography.withFamilies(body: FontFamily, display: FontFamily): Typography {
    fun TextStyle.b() = copy(fontFamily = body, letterSpacing = 0.sp)
    fun TextStyle.d(weight: FontWeight) = copy(fontFamily = display, fontWeight = weight)
    return copy(
        displayLarge = displayLarge.d(FontWeight.Light),
        displayMedium = displayMedium.d(FontWeight.Light),
        displaySmall = displaySmall.d(FontWeight.Light),
        headlineLarge = headlineLarge.d(FontWeight.Light),
        headlineMedium = headlineMedium.d(FontWeight.Light),
        headlineSmall = headlineSmall.d(FontWeight.Normal),
        titleLarge = titleLarge.d(FontWeight.Normal),
        titleMedium = titleMedium.b(), titleSmall = titleSmall.b(),
        bodyLarge = bodyLarge.b(), bodyMedium = bodyMedium.b(), bodySmall = bodySmall.b(),
        labelLarge = labelLarge.b(), labelMedium = labelMedium.b(), labelSmall = labelSmall.b(),
    )
}
