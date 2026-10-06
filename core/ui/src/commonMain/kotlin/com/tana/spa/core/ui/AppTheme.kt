package com.tana.spa.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

private val SpaColorScheme = lightColorScheme(
    primary = SpaColors.Primary,
    onPrimary = SpaColors.OnPrimary,
    background = SpaColors.Background,
    onBackground = SpaColors.TextPrimary,
    surface = SpaColors.Surface,
    onSurface = SpaColors.TextPrimary,
    onSurfaceVariant = SpaColors.TextSecondary,
    outline = SpaColors.FieldBorder,
    outlineVariant = SpaColors.ButtonBorder,
    error = SpaColors.Error,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val fontFamily = poppinsFontFamily()
    val typography = remember(fontFamily) { Typography().withFontFamily(fontFamily) }
    MaterialTheme(colorScheme = SpaColorScheme, typography = typography, content = content)
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
