package com.tana.spa.core.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG 2.2 relative luminance (1.4.3), for asserting that a text/background pair is readable. */
private fun luminance(color: Color): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

fun contrastRatio(foreground: Color, background: Color): Double {
    val a = luminance(foreground)
    val b = luminance(background)
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + 0.05) / (darker + 0.05)
}
