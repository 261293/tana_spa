package com.tana.spa.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaWindow
import com.tana.spa.core.ui.SpaWindowSize

/**
 * One page of content: centred, capped at [maxWidth] and guttered by window size.
 *
 * A browser window can be two thousand pixels wide; text that runs the whole way is unreadable and
 * a row whose ends are a metre apart is unscannable. Every screen puts its body in one of these.
 */
@Composable
fun SpaPage(
    modifier: Modifier = Modifier,
    maxWidth: Dp = SpaPageWidth.Wide,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val gutter = when (SpaWindow.size) {
        SpaWindowSize.COMPACT -> 20.dp
        SpaWindowSize.MEDIUM -> 24.dp
        SpaWindowSize.EXPANDED -> 32.dp
        SpaWindowSize.LARGE -> 40.dp
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(horizontal = gutter)
                .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
