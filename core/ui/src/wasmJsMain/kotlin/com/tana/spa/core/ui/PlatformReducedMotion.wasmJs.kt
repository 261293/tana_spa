package com.tana.spa.core.ui

import androidx.compose.runtime.Composable

// Read through js(...) rather than an external declaration: a host without matchMedia must leave
// the module instantiable, which a missing external would not.
private fun prefersReducedMotion(): Boolean = js(
    """
    typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    """,
)

@Composable
internal actual fun platformPrefersReducedMotion(): Boolean = prefersReducedMotion()
