package com.tana.spa.core.ui

import androidx.compose.runtime.Composable

private fun prefersReducedMotion(): Boolean = js(
    """
    typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    """,
)

@Composable
internal actual fun platformPrefersReducedMotion(): Boolean = prefersReducedMotion()
