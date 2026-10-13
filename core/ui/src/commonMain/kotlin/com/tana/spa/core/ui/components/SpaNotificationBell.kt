package com.tana.spa.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme

private const val MAX_SHOWN_COUNT = 9

/**
 * The bell and its red dot. [unreadCount] 0 draws no badge at all.
 *
 * [contentDescription] is expected to already include the count, so a screen reader announces
 * "Notifications, 3 unread" rather than reading a badge that is drawn as a dot.
 */
@Composable
fun SpaNotificationBell(icon: Painter, contentDescription: String, unreadCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Box {
            Icon(painter = icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurface)
            if (unreadCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).size(16.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            // "9+" rather than a wider badge: the exact number past nine does not
                            // change what the user does next.
                            text = if (unreadCount > MAX_SHOWN_COUNT) "$MAX_SHOWN_COUNT+" else unreadCount.toString(),
                            style = SpaTheme.textStyles.overline,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
