package com.tana.spa.core.ui

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
internal actual fun platformPrefersReducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
