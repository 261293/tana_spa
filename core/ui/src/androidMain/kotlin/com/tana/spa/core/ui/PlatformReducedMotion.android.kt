package com.tana.spa.core.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// "Remove animations" in Accessibility settings sets ANIMATOR_DURATION_SCALE to zero, and it is the
// signal the platform itself honours, so reading it keeps the app in step with the rest of the system.
@Composable
internal actual fun platformPrefersReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
