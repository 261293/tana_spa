package com.tana.spa.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class SpaMotion(val short: Int, val medium: Int, val long: Int, val reduced: Boolean) {
    val exit: Int get() = medium * EXIT_NUMERATOR / EXIT_DENOMINATOR

    private companion object {
        const val EXIT_NUMERATOR = 2
        const val EXIT_DENOMINATOR = 3
    }
}

internal val FullMotion = SpaMotion(short = 150, medium = 250, long = 400, reduced = false)

internal val NoMotion = SpaMotion(short = 0, medium = 0, long = 0, reduced = true)

internal val LocalSpaMotion = staticCompositionLocalOf { FullMotion }

@Composable
internal expect fun platformPrefersReducedMotion(): Boolean
