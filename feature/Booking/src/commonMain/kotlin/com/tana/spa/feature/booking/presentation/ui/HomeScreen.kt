package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaPhotos
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.SpaWindow
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaImageTile
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaSectionTitle
import com.tana.spa.core.ui.components.SpaSkeleton
import com.tana.spa.core.ui.formatPrice
import com.tana.spa.core.ui.localizedLabel
import com.tana.spa.core.ui.rememberSpaScrollState
import com.tana.spa.core.ui.revealOnScroll
import com.tana.spa.feature.booking.domain.Product
import com.tana.spa.feature.booking.presentation.HomeEvent
import com.tana.spa.feature.booking.presentation.HomeState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.home_failed
import com.tana.spa.feature.booking.resources.home_offers
import com.tana.spa.feature.booking.resources.home_offers_action
import com.tana.spa.feature.booking.resources.home_products
import com.tana.spa.feature.booking.resources.home_products_empty
import com.tana.spa.feature.booking.resources.home_products_note
import com.tana.spa.feature.booking.resources.home_retry
import com.tana.spa.feature.booking.resources.home_subtitle
import com.tana.spa.feature.booking.resources.home_subtitle_staff
import com.tana.spa.feature.booking.resources.home_title
import com.tana.spa.feature.booking.resources.home_title_accent
import com.tana.spa.feature.booking.resources.home_treatment_book
import com.tana.spa.feature.booking.resources.home_treatment_meta
import com.tana.spa.feature.booking.resources.home_treatments
import com.tana.spa.shared.spa.Service
import org.jetbrains.compose.resources.stringResource

private val TileShape = RoundedCornerShape(12.dp)
private val TILE_SIZE = 72.dp
private const val SKELETONS = 4

@Composable
fun HomeScreen(state: HomeState, onEvent: (HomeEvent) -> Unit, onBookService: (String) -> Unit, onOpenOffers: () -> Unit) {
    val columns = catalogueColumns(SpaWindow.size).coerceAtLeast(if (SpaWindow.size.isCompact) 1 else 2)
    SpaPage(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberSpaScrollState()),
        maxWidth = SpaPageWidth.Full,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SpaScreenHeader(
            title = stringResource(Res.string.home_title),
            accent = stringResource(Res.string.home_title_accent),
            subtitle = stringResource(if (state.canBook) Res.string.home_subtitle else Res.string.home_subtitle_staff),
            modifier = Modifier.revealOnScroll(),
        )
        if (state.liveOffers > 0) OffersBanner(state.liveOffers, onOpenOffers)

        SpaSectionTitle(stringResource(Res.string.home_treatments))
        when {
            state.loading -> repeat(SKELETONS) { SpaSkeleton(TILE_SIZE + 24.dp) }

            state.failed -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.home_failed),
                    style = SpaTheme.textStyles.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SpaSecondaryButton(text = stringResource(Res.string.home_retry), onClick = { onEvent(HomeEvent.Retry) })
            }

            else -> Grid(state.services, columns) { service ->
                TreatmentCard(service, onBook = if (state.canBook) ({ onBookService(service.id) }) else null)
            }
        }

        if (!state.loading && !state.failed) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SpaSectionTitle(stringResource(Res.string.home_products))
                Text(
                    stringResource(if (state.products.isEmpty()) Res.string.home_products_empty else Res.string.home_products_note),
                    style = SpaTheme.textStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.products.isNotEmpty()) Grid(state.products, columns) { ProductCard(it) }
        }
    }
}

@Composable
private fun <T> Grid(items: List<T>, columns: Int, card: @Composable (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        staggeredColumns(items, columns).forEach { column ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                column.forEach { card(it) }
            }
        }
    }
}

@Composable
private fun OffersBanner(count: Int, onOpen: () -> Unit) {
    SpaCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        onClick = onOpen,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SpaIconImage(SpaIcon.Tag, size = 24.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text = stringResource(Res.string.home_offers, count),
                style = SpaTheme.textStyles.body,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.home_offers_action),
                style = SpaTheme.textStyles.tag,
                color = MaterialTheme.colorScheme.primary,
            )
            SpaIconImage(SpaIcon.ChevronRight, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TreatmentCard(service: Service, onBook: (() -> Unit)?) {
    SpaCard(modifier = Modifier.fillMaxWidth(), onClick = onBook) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SpaImageTile(Modifier.size(TILE_SIZE), shape = TileShape, photo = SpaPhotos.treatment(service.id))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    service.name,
                    style = SpaTheme.textStyles.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(Res.string.home_treatment_meta, service.minutes, service.category.localizedLabel()),
                    style = SpaTheme.textStyles.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatPrice(service.priceVnd),
                        style = SpaTheme.textStyles.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (onBook != null) {
                        Text(
                            stringResource(Res.string.home_treatment_book),
                            style = SpaTheme.textStyles.tag,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product) {
    SpaCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SpaImageTile(Modifier.size(TILE_SIZE), shape = TileShape, icon = SpaIcon.Leaf)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (product.brand.isNotBlank()) {
                    Text(product.brand, style = SpaTheme.textStyles.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    product.name,
                    style = SpaTheme.textStyles.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (product.description.isNotBlank()) {
                    Text(
                        product.description,
                        style = SpaTheme.textStyles.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    listOf(formatPrice(product.priceVnd), product.size).filter { it.isNotBlank() }.joinToString(" · "),
                    style = SpaTheme.textStyles.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
