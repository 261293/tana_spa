package com.tana.spa.core.ui.scafold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tana.spa.core.ui.SpaWindow
import com.tana.spa.core.ui.SpaWindowSize
import com.tana.spa.core.ui.components.AppTopBar
import com.tana.spa.core.ui.components.NavBar
import com.tana.spa.core.ui.components.SpaNavigationRail
import com.tana.spa.core.ui.model.NavConfig

/**
 * The guest app's frame, which changes shape with the window:
 *
 * - compact: destinations in a bottom navigation bar (Material's default at phone width);
 * - medium: a navigation rail beside the content;
 * - expanded and wider: the destinations move into the app bar, as the design file and the web
 *   both expect.
 */
@Composable
fun AppScaffold(
    navItems: List<NavConfig>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val size = SpaWindow.size
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                destinations = if (size.isWide) navItems else emptyList(),
                selectedRoute = selectedRoute,
                onSelect = onSelect,
                actions = topBarActions,
            )
        },
        bottomBar = { if (size.isCompact && navItems.isNotEmpty()) NavBar(navItems, selectedRoute, onSelect) },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (size == SpaWindowSize.MEDIUM && navItems.isNotEmpty()) {
                SpaNavigationRail(navItems, selectedRoute, onSelect)
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}
