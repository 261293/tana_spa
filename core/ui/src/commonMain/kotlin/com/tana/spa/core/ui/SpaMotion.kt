package com.tana.spa.core.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

object SpaEasing {
    val OutExpo: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val InOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
}

@Immutable
data class SpaMotion(val short: Int, val medium: Int, val long: Int, val reveal: Int, val stagger: Int, val reduced: Boolean) {
    val exit: Int get() = medium * EXIT_NUMERATOR / EXIT_DENOMINATOR

    fun staggerFor(order: Int): Int = stagger * order.coerceIn(0, LAST_STAGGER_SLOT)

    private companion object {
        const val EXIT_NUMERATOR = 2
        const val EXIT_DENOMINATOR = 3
        const val LAST_STAGGER_SLOT = 3
    }
}

internal val FullMotion = SpaMotion(short = 160, medium = 220, long = 320, reveal = 520, stagger = 60, reduced = false)

internal val NoMotion = SpaMotion(short = 0, medium = 0, long = 0, reveal = 0, stagger = 0, reduced = true)

internal val LocalSpaMotion = staticCompositionLocalOf { FullMotion }

@Composable
internal expect fun platformPrefersReducedMotion(): Boolean
