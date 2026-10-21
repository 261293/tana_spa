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
import com.tana.spa.core.ui.resources.Res
import com.tana.spa.core.ui.resources.poppins_bold
import com.tana.spa.core.ui.resources.poppins_medium
import com.tana.spa.core.ui.resources.poppins_regular
import com.tana.spa.core.ui.resources.poppins_semibold
import org.jetbrains.compose.resources.Font

/** Poppins, bundled as a compose resource so it renders identically on web, Android and iOS. */
@Composable
fun poppinsFontFamily(): FontFamily = FontFamily(
    Font(Res.font.poppins_regular, FontWeight.Normal),
    Font(Res.font.poppins_medium, FontWeight.Medium),
    Font(Res.font.poppins_semibold, FontWeight.SemiBold),
    Font(Res.font.poppins_bold, FontWeight.Bold),
)

internal val SpaColorScheme = lightColorScheme(
    primary = SpaColors.Primary,
    onPrimary = SpaColors.OnPrimary,
    primaryContainer = SpaColors.Celadon,
    onPrimaryContainer = SpaColors.Primary,
    secondary = SpaColors.Promo,
    onSecondary = SpaColors.OnPrimary,
    secondaryContainer = SpaColors.PromoContainer,
    onSecondaryContainer = SpaColors.Promo,
    tertiary = SpaColors.Warning,
    onTertiary = SpaColors.OnPrimary,
    tertiaryContainer = SpaColors.WarningContainer,
    onTertiaryContainer = SpaColors.Warning,
    background = SpaColors.Background,
    onBackground = SpaColors.TextPrimary,
    surface = SpaColors.Surface,
    onSurface = SpaColors.TextPrimary,
    surfaceVariant = SpaColors.Tone1,
    onSurfaceVariant = SpaColors.TextSecondary,
    surfaceContainerLowest = SpaColors.Background,
    surfaceContainerLow = SpaColors.Surface,
    surfaceContainer = SpaColors.Tone1,
    surfaceContainerHigh = SpaColors.Tone2,
    surfaceContainerHighest = SpaColors.Celadon,
    outline = SpaColors.FieldBorder,
    outlineVariant = SpaColors.ButtonBorder,
    error = SpaColors.Error,
    errorContainer = SpaColors.ErrorContainer,
    onErrorContainer = SpaColors.OnErrorContainer,
)

/**
 * Softly squared, not pills: chips and fields 6dp, buttons 10dp, cards 14dp, the action pocket
 * 16dp. [SpaPebbleShape] is the asymmetric "pebble" the design uses for its photo moments.
 */
internal val SpaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

val SpaPebbleShape = RoundedCornerShape(topStart = 28.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 28.dp)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val fontFamily = poppinsFontFamily()
    val typography = remember(fontFamily) { Typography().withFontFamily(fontFamily) }
    val textStyles = remember(typography) { spaTextStyles(typography) }
    val motion = if (platformPrefersReducedMotion()) NoMotion else FullMotion
    CompositionLocalProvider(
        LocalSpaExtraColors provides LightExtraColors,
        LocalSpaTextStyles provides textStyles,
        LocalSpaMotion provides motion,
    ) {
        MaterialTheme(colorScheme = SpaColorScheme, typography = typography, shapes = SpaShapes, content = content)
    }
}

private fun Typography.withFontFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}
