package com.tana.spa.core.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material's width breakpoints, which are the only ones this app uses:
 * compact `< 600dp`, medium `600–839`, expanded `840–1199`, large `>= 1200`.
 *
 * A screen asks `SpaWindow.size`, never the platform. A browser window is simply a window that is
 * usually expanded or large and can be dragged down to compact, which is why there is no "web
 * layout" — there are width layouts, and the web gets whichever one it is wide enough for.
 */
enum class SpaWindowSize {
    COMPACT,
    MEDIUM,
    EXPANDED,
    LARGE,
    ;

    val isCompact: Boolean get() = this == COMPACT

    /** True from [MEDIUM] up: the point where a bottom bar gives way to side navigation. */
    val hasSideNavigation: Boolean get() = this != COMPACT

    /** True from [EXPANDED] up: the point where content can sit in more than one column. */
    val isWide: Boolean get() = this == EXPANDED || this == LARGE
}

object SpaBreakpoints {
    val Medium: Dp = 600.dp
    val Expanded: Dp = 840.dp
    val Large: Dp = 1200.dp
}

/** Page widths from the design file; content is centred and never runs wider than these. */
object SpaPageWidth {
    /** Reading width: the notification centre and settings. */
    val Reading: Dp = 720.dp

    /** A list with a hero: offers. */
    val Wide: Dp = 1000.dp

    /** Multi-column: booking and the staff console. */
    val Full: Dp = 1240.dp
}

val LocalSpaWindowSize = staticCompositionLocalOf { SpaWindowSize.COMPACT }

object SpaWindow {
    val size: SpaWindowSize
        @Composable @ReadOnlyComposable
        get() = LocalSpaWindowSize.current
}

fun spaWindowSizeOf(width: Dp): SpaWindowSize = when {
    width < SpaBreakpoints.Medium -> SpaWindowSize.COMPACT
    width < SpaBreakpoints.Expanded -> SpaWindowSize.MEDIUM
    width < SpaBreakpoints.Large -> SpaWindowSize.EXPANDED
    else -> SpaWindowSize.LARGE
}

/**
 * Measures the window once, at the root, and publishes the size class to every screen below. One
 * measurement means one answer: a screen and its app bar cannot disagree about how wide the window
 * is, which is what happens when each one calls `BoxWithConstraints` for itself.
 */
@Composable
fun ProvideSpaWindowSize(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(LocalSpaWindowSize provides spaWindowSizeOf(maxWidth)) {
            content()
        }
    }
}
