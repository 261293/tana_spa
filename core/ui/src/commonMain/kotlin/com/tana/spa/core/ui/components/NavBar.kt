package com.tana.spa.core.ui.components

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tana.spa.core.ui.model.NavConfig

@Composable
fun NavBar(
    items: List<NavConfig>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
) {
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.route == selectedRoute,
                onClick = { onSelect(item.route) },
                icon = {},
                label = { Text(item.label) },
            )
        }
    }
}
