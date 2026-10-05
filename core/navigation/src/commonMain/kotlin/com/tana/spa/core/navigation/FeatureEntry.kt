package com.tana.spa.core.navigation

import androidx.compose.runtime.Composable

/**
 * A self-contained, navigable destination. Every feature module contributes one
 * so that the app module can wire up navigation without depending on feature internals.
 */
interface FeatureEntry {
    val route: String

    @Composable
    fun Content(navigator: Navigator)
}

/** Minimal navigation surface exposed to features. */
interface Navigator {
    fun navigate(route: String)
    fun back()
}
