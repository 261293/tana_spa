package com.tana.spa.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Animation durations in milliseconds, read through [SpaTheme] so a screen never names its own.
 * Every duration is zero once the platform asks for reduced motion, which is what makes that one
 * system setting reach the whole app instead of the call sites someone remembered to update.
 */
@Immutable
data class SpaMotion(
    /** A selection or another small state change. */
    val short: Int,
    /** Content arriving or leaving, and moving between routes. */
    val medium: Int,
    /** Progress the guest watches advance. */
    val long: Int,
    /**
     * Looping and decorative animation is dropped entirely when this is set, rather than run at
     * zero duration: an endless animation has no end to snap to, and a zero-length loop spins.
     */
    val reduced: Boolean,
) {
    /** Leaving is quicker than arriving, so a dismissal reads as answered rather than slow. */
    val exit: Int get() = medium * EXIT_NUMERATOR / EXIT_DENOMINATOR

    private companion object {
        const val EXIT_NUMERATOR = 2
        const val EXIT_DENOMINATOR = 3
    }
}

internal val FullMotion = SpaMotion(short = 150, medium = 250, long = 400, reduced = false)

internal val NoMotion = SpaMotion(short = 0, medium = 0, long = 0, reduced = true)

internal val LocalSpaMotion = staticCompositionLocalOf { FullMotion }

/**
 * Whether the platform asks for less motion, read while the theme composes.
 *
 * shortcut: the value is not observed, so toggling the system setting applies on the next launch;
 * add a per-platform listener if it ever has to take effect while the app is open.
 */
@Composable
internal expect fun platformPrefersReducedMotion(): Boolean
