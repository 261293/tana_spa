package com.tana.spa.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class SpaRevealFrom {
    Bottom,
    Start,
    End,
}

private val RevealMargin = 72.dp

@Composable
fun Modifier.revealOnScroll(order: Int = 0, from: SpaRevealFrom = SpaRevealFrom.Bottom): Modifier {
    val motion = SpaTheme.motion
    if (motion.reduced) return this

    val viewportHeight = SpaWindow.viewportHeight
    val density = LocalDensity.current
    val travel = with(density) { revealTravelFor(SpaWindow.size).toPx() }
    val margin = with(density) { RevealMargin.toPx() }
    val startEdge = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f

    var reached by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (reached) 1f else 0f) }

    LaunchedEffect(reached) {
        if (reached) progress.animateTo(1f, tween(motion.reveal, motion.staggerFor(order), SpaEasing.OutExpo))
    }

    return onGloballyPositioned { coordinates ->
        if (!reached) reached = hasReachedViewport(coordinates.boundsInRoot().top, viewportHeight, margin)
    }.graphicsLayer {
        val remaining = 1f - progress.value
        alpha = progress.value
        when (from) {
            SpaRevealFrom.Bottom -> translationY = travel * remaining
            SpaRevealFrom.Start -> translationX = travel * remaining * startEdge
            SpaRevealFrom.End -> translationX = travel * remaining * -startEdge
        }
    }
}

internal fun hasReachedViewport(topInRoot: Float, viewportHeight: Int, margin: Float): Boolean =
    viewportHeight <= UNMEASURED_VIEWPORT || topInRoot < viewportHeight - margin

internal fun revealTravelFor(size: SpaWindowSize): Dp = when (size) {
    SpaWindowSize.COMPACT -> 24.dp
    SpaWindowSize.MEDIUM -> 32.dp
    SpaWindowSize.EXPANDED, SpaWindowSize.LARGE -> 40.dp
}
