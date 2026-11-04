package com.tana.spa.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.spaClickable
import org.jetbrains.compose.resources.DrawableResource

private val CardShape = RoundedCornerShape(18.dp)
private const val NAME_LINES = 2

/**
 * A tile for something that has a name and a price (a service, a product): picture area on top,
 * name, price and an optional [detail] line below. Names always take [NAME_LINES] lines so tiles in
 * one row stay the same height.
 */
@Composable
fun SpaCatalogCard(
    title: String,
    priceLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageHeight: Dp = 120.dp,
    detail: String? = null,
    badge: String? = null,
    photo: DrawableResource? = null,
) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .spaClickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(imageHeight)) {
            SpaImageTile(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                photo = photo,
                contentDescription = if (photo != null) title else null,
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = SpaTheme.textStyles.overline,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = SpaTheme.textStyles.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = NAME_LINES,
                maxLines = NAME_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = priceLabel,
                style = SpaTheme.textStyles.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = SpaTheme.textStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
