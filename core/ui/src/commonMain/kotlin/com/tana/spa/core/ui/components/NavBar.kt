package com.tana.spa.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.model.NavConfig

/** Phone-width navigation: Material's bottom bar. */
@Composable
fun NavBar(items: List<NavConfig>, selectedRoute: String, onSelect: (String) -> Unit) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.route == selectedRoute,
                onClick = { onSelect(item.route) },
                icon = { NavIcon(item) },
                label = { Text(item.label, style = SpaTheme.textStyles.label) },
                colors = colors,
            )
        }
    }
}

/** Tablet-width navigation: Material's rail, which is its default above compact. */
@Composable
fun SpaNavigationRail(items: List<NavConfig>, selectedRoute: String, onSelect: (String) -> Unit) {
    val colors = NavigationRailItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { item ->
            NavigationRailItem(
                selected = item.route == selectedRoute,
                onClick = { onSelect(item.route) },
                icon = { NavIcon(item) },
                label = { Text(item.label, style = SpaTheme.textStyles.label) },
                colors = colors,
            )
        }
    }
}

/**
 * Desktop-width navigation: the destinations sit in the app bar, where a visitor to a web page
 * looks for them. Material's default above compact is the rail (see `docs/material-design.md`);
 * the design file puts them in the bar on a desk screen, and the web expects that.
 */
@Composable
fun SpaTopBarDestinations(items: List<NavConfig>, selectedRoute: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            FilledTonalButton(
                onClick = { onSelect(item.route) },
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
                modifier = Modifier.semantics {
                    this.selected = selected
                    role = Role.Tab
                },
            ) {
                NavIcon(item)
                Text(
                    text = item.label,
                    style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NavIcon(item: NavConfig) {
    BadgedBox(
        badge = {
            if (item.badgeCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Text(item.badgeCount.toString(), style = SpaTheme.textStyles.overline)
                }
            }
        },
    ) {
        SpaIconImage(item.icon, size = 22.dp)
    }
}
