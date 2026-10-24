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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.resources.Res
import com.tana.spa.core.ui.resources.fraunces_italic
import com.tana.spa.core.ui.resources.fraunces_medium
import com.tana.spa.core.ui.resources.fraunces_regular
import com.tana.spa.core.ui.resources.instrument_sans_bold
import com.tana.spa.core.ui.resources.instrument_sans_medium
import com.tana.spa.core.ui.resources.instrument_sans_regular
import com.tana.spa.core.ui.resources.instrument_sans_semibold
import org.jetbrains.compose.resources.Font

/** Instrument Sans, bundled so body copy renders identically on web, Android, iOS and desktop. */
@Composable
fun instrumentSansFontFamily(): FontFamily = FontFamily(
    Font(Res.font.instrument_sans_regular, FontWeight.Normal),
    Font(Res.font.instrument_sans_medium, FontWeight.Medium),
    Font(Res.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.instrument_sans_bold, FontWeight.Bold),
)

/**
 * Fraunces, the display serif. Both files are static instances cut at `opsz=30, SOFT=0, WONK=0`:
 * the variable font defaults to `wght=900, opsz=9, WONK=1`, which is not the face the design uses.
 */
@Composable
fun frauncesFontFamily(): FontFamily = FontFamily(
    Font(Res.font.fraunces_regular, FontWeight.Normal),
    Font(Res.font.fraunces_medium, FontWeight.Medium),
    Font(Res.font.fraunces_italic, FontWeight.Normal, FontStyle.Italic),
)

internal val SpaColorScheme = lightColorScheme(
    primary = SpaColors.Forest,
    onPrimary = SpaColors.OnForest,
    primaryContainer = SpaColors.SageTint,
    onPrimaryContainer = SpaColors.Forest,
    secondary = SpaColors.Forest600,
    onSecondary = SpaColors.OnForest,
    secondaryContainer = SpaColors.SageSoft,
    onSecondaryContainer = SpaColors.Forest600,
    tertiary = SpaColors.Clay,
    onTertiary = SpaColors.OnForest,
    tertiaryContainer = SpaColors.ClaySoft,
    onTertiaryContainer = SpaColors.Danger,
    background = SpaColors.Ground,
    onBackground = SpaColors.Ink,
    surface = SpaColors.Surface,
    onSurface = SpaColors.Ink,
    surfaceVariant = SpaColors.SurfaceAlt,
    onSurfaceVariant = SpaColors.Muted,
    surfaceContainerLowest = SpaColors.Surface,
    surfaceContainerLow = SpaColors.Ground,
    surfaceContainer = SpaColors.SurfaceAlt,
    surfaceContainerHigh = SpaColors.SageSoft,
    surfaceContainerHighest = SpaColors.SageTint,
    outline = SpaColors.LineStrong,
    outlineVariant = SpaColors.Line,
    error = SpaColors.Danger,
    errorContainer = SpaColors.ClaySoft,
    onErrorContainer = SpaColors.DangerDeep,
)

/** The design's radius scale: 10 on fields and chips, 16 on panels, 18 on cards, 22 on sheets. */
internal val SpaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

val SpaPebbleShape = RoundedCornerShape(topStart = 28.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 28.dp)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val bodyFamily = instrumentSansFontFamily()
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
    fun TextStyle.b() = copy(fontFamily = body)
    fun TextStyle.d() = copy(fontFamily = display, fontWeight = FontWeight.Normal)
    return copy(
        displayLarge = displayLarge.d(), displayMedium = displayMedium.d(), displaySmall = displaySmall.d(),
        headlineLarge = headlineLarge.d(), headlineMedium = headlineMedium.d(), headlineSmall = headlineSmall.d(),
        titleLarge = titleLarge.d(), titleMedium = titleMedium.b(), titleSmall = titleSmall.b(),
        bodyLarge = bodyLarge.b(), bodyMedium = bodyMedium.b(), bodySmall = bodySmall.b(),
        labelLarge = labelLarge.b(), labelMedium = labelMedium.b(), labelSmall = labelSmall.b(),
    )
}
