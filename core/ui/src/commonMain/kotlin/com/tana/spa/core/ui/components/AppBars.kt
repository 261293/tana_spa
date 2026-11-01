package com.tana.spa.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.LocalSpaScrollLift
import com.tana.spa.core.ui.SpaEasing
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaTheme
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
    val lifted = LocalSpaScrollLift.current?.lifted == true
    val motion = SpaTheme.motion
    val lift = tween<Float>(motion.medium, easing = SpaEasing.InOut)
    val container by animateColorAsState(
        targetValue = if (lifted) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
        animationSpec = tween(motion.medium, easing = SpaEasing.InOut),
    )
    val edgeAlpha by animateFloatAsState(targetValue = if (lifted) 1f else 0f, animationSpec = lift)

    Box(Modifier.fillMaxWidth().background(container)) {
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
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = if (SpaWindow.size.isWide) SpaPageWidth.Full else Dp.Unspecified),
        )
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = edgeAlpha },
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
