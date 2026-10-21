package com.tana.spa.core.ui

import androidx.compose.runtime.Composable

// The desktop target exposes no system-wide reduced-motion setting to read.
@Composable
internal actual fun platformPrefersReducedMotion(): Boolean = false
