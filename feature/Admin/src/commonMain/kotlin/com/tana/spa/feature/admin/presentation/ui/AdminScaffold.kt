package com.tana.spa.feature.admin.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.SpaWindow
import com.tana.spa.core.ui.components.SpaAvatar
import com.tana.spa.core.ui.components.SpaBrand
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.feature.admin.resources.Res
import com.tana.spa.feature.admin.resources.admin_nav_codes
import com.tana.spa.feature.admin.resources.admin_nav_menu
import com.tana.spa.feature.admin.resources.admin_nav_requests
import com.tana.spa.feature.admin.resources.admin_open_guest_app
import org.jetbrains.compose.resources.stringResource

internal enum class AdminSection { REQUESTS, MENU, CODES }

/**
 * The staff console's frame. Material's navigation rail on a desk screen and its tabs on a phone —
 * the two components Material names for switching views at those sizes. The guest app's bottom bar
 * is deliberately absent: this is a different product for a different user.
 */
@Composable
internal fun AdminScaffold(
    section: AdminSection,
    pendingCount: Int,
    onSelect: (AdminSection) -> Unit,
    onOpenGuestApp: () -> Unit,
    content: @Composable (wide: Boolean) -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // This console draws its own frame rather than a Scaffold, which is what would
            // otherwise keep the tabs out from under the status bar.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val wide = SpaWindow.size.hasSideNavigation
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Rail(section, pendingCount, onSelect, onOpenGuestApp)
                Box(Modifier.weight(1f)) { content(true) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                CompactTabs(section, pendingCount, onSelect, onOpenGuestApp)
                Box(Modifier.weight(1f)) { content(false) }
            }
        }
    }
}

@Composable
private fun Rail(section: AdminSection, pendingCount: Int, onSelect: (AdminSection) -> Unit, onOpenGuestApp: () -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            SpaBrand(Modifier.padding(bottom = 8.dp), compact = true)
        },
    ) {
        val colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NavigationRailItem(
            selected = section == AdminSection.REQUESTS,
            onClick = { onSelect(AdminSection.REQUESTS) },
            icon = { QueueIcon(SpaIcon.CalendarCheck, pendingCount) },
            label = { Text(stringResource(Res.string.admin_nav_requests), style = SpaTheme.textStyles.label) },
            colors = colors,
        )
        NavigationRailItem(
            selected = section == AdminSection.MENU,
            onClick = { onSelect(AdminSection.MENU) },
            icon = { SpaIconImage(SpaIcon.Leaf, size = 22.dp) },
            label = { Text(stringResource(Res.string.admin_nav_menu), style = SpaTheme.textStyles.label) },
            colors = colors,
        )
        NavigationRailItem(
            selected = section == AdminSection.CODES,
            onClick = { onSelect(AdminSection.CODES) },
            icon = { SpaIconImage(SpaIcon.Ticket, size = 22.dp) },
            label = { Text(stringResource(Res.string.admin_nav_codes), style = SpaTheme.textStyles.label) },
            colors = colors,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenGuestApp) {
            SpaIconImage(
                icon = SpaIcon.External,
                size = 22.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = stringResource(Res.string.admin_open_guest_app),
            )
        }
        SpaAvatar("RM", Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun CompactTabs(section: AdminSection, pendingCount: Int, onSelect: (AdminSection) -> Unit, onOpenGuestApp: () -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpaBrand(Modifier.weight(1f))
            IconButton(onClick = onOpenGuestApp) {
                SpaIconImage(
                    icon = SpaIcon.External,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = stringResource(Res.string.admin_open_guest_app),
                )
            }
        }
        PrimaryTabRow(
            selectedTabIndex = section.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Tab(
                selected = section == AdminSection.REQUESTS,
                onClick = { onSelect(AdminSection.REQUESTS) },
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.admin_nav_requests),
                            style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
                        )
                        if (pendingCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                            ) {
                                Text(pendingCount.toString(), style = SpaTheme.textStyles.overline)
                            }
                        }
                    }
                },
            )
            Tab(
                selected = section == AdminSection.MENU,
                onClick = { onSelect(AdminSection.MENU) },
                text = {
                    Text(
                        text = stringResource(Res.string.admin_nav_menu),
                        style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
                    )
                },
            )
            Tab(
                selected = section == AdminSection.CODES,
                onClick = { onSelect(AdminSection.CODES) },
                text = {
                    Text(
                        text = stringResource(Res.string.admin_nav_codes),
                        style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
                    )
                },
            )
        }
    }
}

@Composable
private fun QueueIcon(icon: SpaIcon, count: Int) {
    BadgedBox(
        badge = {
            if (count > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Text(count.toString(), style = SpaTheme.textStyles.overline)
                }
            }
        },
    ) {
        SpaIconImage(icon, size = 22.dp, modifier = Modifier.size(22.dp))
    }
}
