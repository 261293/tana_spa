package com.tana.spa.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme

private val DotSize = 8.dp
private val ActiveWidth = 20.dp
private const val INACTIVE_ALPHA = 0.3f

/** Hidden for a single page. Decorative: the pager itself already tells assistive tech which page it is on. */
@Composable
fun SpaPagerIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    if (pageCount < 2) return

    Row(
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(if (selected) ActiveWidth else DotSize, tween(SpaTheme.motion.short))
            val color = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .width(width)
                    .height(DotSize)
                    .background(if (selected) color else color.copy(alpha = INACTIVE_ALPHA), CircleShape),
            )
        }
    }
}
