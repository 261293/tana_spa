package com.tana.spa.core.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.model.NavConfig
import org.jetbrains.compose.resources.painterResource

private const val INDICATOR_ALPHA = 0.12f

@Composable
fun NavBar(items: List<NavConfig>, selectedRoute: String, onSelect: (String) -> Unit) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = INDICATOR_ALPHA),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.route == selectedRoute,
                onClick = { onSelect(item.route) },
                icon = { item.icon?.let { Icon(painter = painterResource(it), contentDescription = null) } },
                label = { Text(item.label, style = SpaTheme.textStyles.label) },
                colors = colors,
            )
        }
    }
}
