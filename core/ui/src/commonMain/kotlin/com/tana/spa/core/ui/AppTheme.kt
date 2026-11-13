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
import com.tana.spa.core.ui.resources.be_vietnam_pro_medium
import com.tana.spa.core.ui.resources.be_vietnam_pro_regular
import com.tana.spa.core.ui.resources.be_vietnam_pro_semibold
import com.tana.spa.core.ui.resources.fraunces_medium
import com.tana.spa.core.ui.resources.fraunces_regular
import org.jetbrains.compose.resources.Font

@Composable
fun beVietnamProFontFamily(): FontFamily = FontFamily(
    Font(Res.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(Res.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(Res.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
)

@Composable
fun frauncesFontFamily(): FontFamily = FontFamily(
    Font(Res.font.fraunces_regular, FontWeight.Normal),
    Font(Res.font.fraunces_medium, FontWeight.Medium),
)

internal val SpaColorScheme = lightColorScheme(
    primary = SpaColors.Copper,
    onPrimary = SpaColors.OnCopper,
    primaryContainer = SpaColors.CopperTint,
    onPrimaryContainer = SpaColors.CopperDeep,
    secondary = SpaColors.Eucalyptus,
    onSecondary = SpaColors.OnEucalyptus,
    secondaryContainer = SpaColors.EucalyptusTint,
    onSecondaryContainer = SpaColors.EucalyptusDeep,
    tertiary = SpaColors.Slate,
    onTertiary = SpaColors.Card,
    tertiaryContainer = SpaColors.SlateTint,
    onTertiaryContainer = SpaColors.Ink,
    background = SpaColors.Paper,
    onBackground = SpaColors.Ink,
    surface = SpaColors.Card,
    onSurface = SpaColors.Ink,
    surfaceVariant = SpaColors.Paper2,
    onSurfaceVariant = SpaColors.Muted,
    surfaceTint = SpaColors.Copper,
    surfaceContainerLowest = SpaColors.Card,
    surfaceContainerLow = SpaColors.Paper,
    surfaceContainer = SpaColors.Paper2,
    surfaceContainerHigh = SpaColors.Paper3,
    surfaceContainerHighest = SpaColors.SlateTint,
    inverseSurface = SpaColors.Ink,
    inverseOnSurface = SpaColors.Paper,
    inversePrimary = SpaColors.CopperTint,
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
    val bodyFamily = beVietnamProFontFamily()
    val displayFamily = frauncesFontFamily()
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
        displayLarge = displayLarge.d(FontWeight.Normal),
        displayMedium = displayMedium.d(FontWeight.Normal),
        displaySmall = displaySmall.d(FontWeight.Normal),
        headlineLarge = headlineLarge.d(FontWeight.Normal),
        headlineMedium = headlineMedium.d(FontWeight.Normal),
        headlineSmall = headlineSmall.d(FontWeight.Normal),
        titleLarge = titleLarge.d(FontWeight.Normal),
        titleMedium = titleMedium.b(), titleSmall = titleSmall.b(),
        bodyLarge = bodyLarge.b(), bodyMedium = bodyMedium.b(), bodySmall = bodySmall.b(),
        labelLarge = labelLarge.b(), labelMedium = labelMedium.b(), labelSmall = labelSmall.b(),
    )
}
