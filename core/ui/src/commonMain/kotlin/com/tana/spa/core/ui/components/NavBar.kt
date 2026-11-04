package com.tana.spa.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.model.NavConfig
import com.tana.spa.core.ui.spaClickable

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
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            // A filled chip here only reads while the bar keeps its resting colour: once the bar
            // lifts on scroll every chip shows up and none of them looks chosen. The design marks
            // the place with weight, full ink and a rule under the word instead.
            val ink = if (selected) MaterialTheme.colorScheme.onSurface else SpaTheme.extraColors.inkSoft
            val rule = MaterialTheme.colorScheme.secondary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .spaClickable(role = Role.Tab) { onSelect(item.route) }
                    .semantics { this.selected = selected }
                    .drawBehind {
                        if (selected) {
                            val width = 1.dp.toPx()
                            val y = size.height - width / 2
                            drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = width)
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 12.dp),
            ) {
                NavIcon(item, ink)
                Text(
                    text = item.label,
                    style = SpaTheme.textStyles.body.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = ink,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NavIcon(item: NavConfig, tint: Color = LocalContentColor.current) {
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
        SpaIconImage(item.icon, size = 22.dp, tint = tint)
    }
}
