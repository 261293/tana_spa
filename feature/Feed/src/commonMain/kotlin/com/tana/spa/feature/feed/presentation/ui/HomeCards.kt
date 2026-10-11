package com.tana.spa.feature.feed.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaCatalogCard
import com.tana.spa.core.ui.components.SpaPagerIndicator
import com.tana.spa.core.ui.components.spaShadow
import com.tana.spa.feature.feed.presentation.data.UiAppointment
import com.tana.spa.feature.feed.presentation.data.UiBanner
import com.tana.spa.feature.feed.presentation.data.UiCategory
import com.tana.spa.feature.feed.presentation.data.UiProduct
import com.tana.spa.feature.feed.presentation.data.UiService
import com.tana.spa.feature.feed.resources.Res
import com.tana.spa.feature.feed.resources.home_appointment_upcoming
import com.tana.spa.feature.feed.resources.home_assistant_action
import com.tana.spa.feature.feed.resources.home_assistant_overline
import com.tana.spa.feature.feed.resources.home_assistant_title
import com.tana.spa.feature.feed.resources.home_combo
import com.tana.spa.feature.feed.resources.home_duration
import org.jetbrains.compose.resources.stringResource

private val CardShape = RoundedCornerShape(20.dp)
private const val TINT_ALPHA = 0.12f
private const val TEXT_WIDTH_FRACTION = 0.72f

@Composable
internal fun AssistantCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .spaShadow(8.dp, CardShape)
            .background(Brush.linearGradient(SpaTheme.extraColors.heroGradient), CardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(TEXT_WIDTH_FRACTION),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.home_assistant_overline),
                style = SpaTheme.textStyles.label,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.home_assistant_title),
                style = SpaTheme.textStyles.screenTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.home_assistant_action),
                style = SpaTheme.textStyles.buttonLabel,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
internal fun AppointmentCard(appointment: UiAppointment, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .spaShadow(4.dp, CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = appointment.serviceName,
                style = SpaTheme.textStyles.prompt,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.home_appointment_upcoming),
                style = SpaTheme.textStyles.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun BannerCard(banner: UiBanner, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(144.dp)
            .spaShadow(4.dp, CardShape)
            .background(Brush.linearGradient(SpaTheme.extraColors.cardGradient), CardShape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = TINT_ALPHA), CircleShape),
        )
        Text(
            text = banner.title,
            style = SpaTheme.textStyles.prompt,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(TEXT_WIDTH_FRACTION)
                .padding(16.dp),
        )
    }
}

@Composable
internal fun CategoryTile(category: UiCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = TINT_ALPHA), CircleShape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = category.name.take(1).uppercase(),
                style = SpaTheme.textStyles.screenTitle,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = category.name,
            style = SpaTheme.textStyles.label,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ServiceCard(service: UiService, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SpaCatalogCard(
        title = service.name,
        priceLabel = service.priceLabel,
        onClick = onClick,
        modifier = modifier.width(200.dp),
        imageHeight = 112.dp,
        detail = stringResource(Res.string.home_duration, service.durationMinutes),
        badge = if (service.isCombo) stringResource(Res.string.home_combo) else null,
    )
}

@Composable
internal fun ProductCard(product: UiProduct, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SpaCatalogCard(
        title = product.name,
        priceLabel = product.priceLabel,
        onClick = onClick,
        modifier = modifier.width(160.dp),
    )
}

@Composable
internal fun BannerPager(banners: List<UiBanner>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState { banners.size }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            pageSpacing = 12.dp,
            key = { banners[it].id },
        ) { page ->
            BannerCard(banners[page], onClick = onClick)
        }
        SpaPagerIndicator(pageCount = banners.size, currentPage = pagerState.currentPage)
    }
}
