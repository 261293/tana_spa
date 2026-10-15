package com.tana.spa.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaWindow
import com.tana.spa.core.ui.model.NavConfig

/**
 * The guest app's bar: the house mark on the left, the account and any action on the right, and —
 * on a desk-sized window — the destinations in between. The bar itself spans the window while its
 * contents stay inside the page width, so nothing drifts to the far corners of a wide browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    destinations: List<NavConfig> = emptyList(),
    selectedRoute: String = "",
    onSelect: (String) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        TopAppBar(
            title = { SpaBrand() },
            actions = {
                if (destinations.isNotEmpty()) {
                    SpaTopBarDestinations(destinations, selectedRoute, onSelect)
                    Box(Modifier.padding(start = 8.dp))
                }
                actions()
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
            modifier = Modifier.widthIn(max = if (SpaWindow.size.isWide) SpaPageWidth.Full else Dp.Unspecified),
        )
    }
}
