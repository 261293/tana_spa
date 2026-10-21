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
    /** The route being shown, including any argument (see `withArg`). */
    val current: String

    /** Entries on the back stack including the one being shown, so the root is 1. */
    val depth: Int

    fun navigate(route: String)

    /** Navigates to [route] and drops everything behind it (e.g. after login, so Back can't return). */
    fun replaceAll(route: String)
    fun back()
}
