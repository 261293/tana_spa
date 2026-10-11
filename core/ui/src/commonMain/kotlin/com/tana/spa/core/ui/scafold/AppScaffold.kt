package com.tana.spa.core.ui.scafold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tana.spa.core.ui.components.AppTopBar
import com.tana.spa.core.ui.components.NavBar
import com.tana.spa.core.ui.model.NavConfig

@Composable
fun AppScaffold(
    title: String,
    navItems: List<NavConfig>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar(title, actions = topBarActions) },
        bottomBar = { if (navItems.isNotEmpty()) NavBar(navItems, selectedRoute, onSelect) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            content()
        }
    }
}
