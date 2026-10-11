package com.tana.spa.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.tana.spa.core.ui.SpaTheme

/** A shadow tinted with the theme's shadow colour, so every raised surface lifts the same way. */
@Composable
fun Modifier.spaShadow(elevation: Dp, shape: Shape): Modifier {
    val color = SpaTheme.extraColors.shadow
    return shadow(elevation, shape, ambientColor = color, spotColor = color)
}
