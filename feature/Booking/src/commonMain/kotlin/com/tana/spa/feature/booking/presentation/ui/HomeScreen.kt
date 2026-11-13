package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.SpaPhotos
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.SpaWindow
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaImageTile
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaSecondaryButton
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
import com.tana.spa.feature.booking.resources.home_hero_accent
import com.tana.spa.feature.booking.resources.home_hero_rest
import com.tana.spa.feature.booking.resources.home_offers
import com.tana.spa.feature.booking.resources.home_offers_action
import com.tana.spa.feature.booking.resources.home_prices_lead
import com.tana.spa.feature.booking.resources.home_prices_title
import com.tana.spa.feature.booking.resources.home_products
import com.tana.spa.feature.booking.resources.home_products_empty
import com.tana.spa.feature.booking.resources.home_products_note
import com.tana.spa.feature.booking.resources.home_quick_action
import com.tana.spa.feature.booking.resources.home_quick_note
import com.tana.spa.feature.booking.resources.home_quick_service
import com.tana.spa.feature.booking.resources.home_quick_title
import com.tana.spa.feature.booking.resources.home_retry
import com.tana.spa.feature.booking.resources.home_stat_minutes
import com.tana.spa.feature.booking.resources.home_stat_minutes_label
import com.tana.spa.feature.booking.resources.home_stat_offers
import com.tana.spa.feature.booking.resources.home_stat_price_label
import com.tana.spa.feature.booking.resources.home_stat_treatments
import com.tana.spa.feature.booking.resources.home_subtitle
import com.tana.spa.feature.booking.resources.home_subtitle_staff
import com.tana.spa.feature.booking.resources.home_treatment_meta
import com.tana.spa.feature.booking.resources.home_treatments_lead
import com.tana.spa.feature.booking.resources.home_treatments_note
import com.tana.spa.feature.booking.resources.home_treatments_title
import com.tana.spa.shared.spa.Service
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val PAGE_MAX = 1160.dp
private val HERO_TITLE_SIZE = 38.sp
private val HERO_TITLE_SIZE_WIDE = 60.sp
private const val SCRIM_ALPHA = 0.58f
private const val SLANT = 0.06f

private val SlantedField = GenericShape { size, _ ->
    moveTo(0f, size.height * SLANT * 2)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
fun HomeScreen(state: HomeState, onEvent: (HomeEvent) -> Unit, onBookService: (String) -> Unit, onOpenOffers: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberSpaScrollState())) {
        Hero(state, onBookService)
        when {
            state.loading -> Section { repeat(3) { SpaSkeleton(88.dp) } }

            state.failed -> Section {
                Text(stringResource(Res.string.home_failed), style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurface)
                SpaSecondaryButton(text = stringResource(Res.string.home_retry), onClick = { onEvent(HomeEvent.Retry) })
            }

            else -> {
                Facts(state)
                Treatments(state, onBookService)
                Prices(state)
                Shelf(state.products)
                if (state.liveOffers > 0) Section { OffersBanner(state.liveOffers, onOpenOffers) }
            }
        }
    }
}

@Composable
private fun Section(field: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (field) Modifier.background(MaterialTheme.colorScheme.surfaceContainer, SlantedField) else Modifier)
            .padding(horizontal = 20.dp, vertical = if (field) 72.dp else 48.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = PAGE_MAX).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
    }
}

@Composable
private fun Hero(state: HomeState, onBookService: (String) -> Unit) {
    val wide = !SpaWindow.size.isCompact
    val bookable = state.canBook && state.services.isNotEmpty()
    Box(Modifier.fillMaxWidth().heightIn(min = if (wide) 560.dp else 420.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(SpaPhotos.hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(Modifier.matchParentSize().background(SpaTheme.extraColors.pocket.copy(alpha = SCRIM_ALPHA)))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = PAGE_MAX).fillMaxWidth()) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                        HeroWords(state, HERO_TITLE_SIZE_WIDE, Modifier.weight(1.2f))
                        if (bookable) QuickBook(state.services, onBookService, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        HeroWords(state, HERO_TITLE_SIZE, Modifier)
                        if (bookable) QuickBook(state.services, onBookService, Modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroWords(state: HomeState, size: TextUnit, modifier: Modifier) {
    val accent = stringResource(Res.string.home_hero_accent)
    val rest = stringResource(Res.string.home_hero_rest)
    val onPhoto = SpaTheme.extraColors.onPhoto
    val accentColor = SpaTheme.extraColors.onPhotoAccent
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = accentColor)) { append(accent) }
                append(" ")
                append(rest)
            },
            style = SpaTheme.textStyles.displayTitle.copy(fontSize = size, lineHeight = size * 1.05f),
            color = onPhoto,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(if (state.canBook) Res.string.home_subtitle else Res.string.home_subtitle_staff),
            style = SpaTheme.textStyles.bodyLoose.copy(fontSize = 17.sp),
            color = onPhoto,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickBook(services: List<Service>, onBookService: (String) -> Unit, modifier: Modifier) {
    var chosenId by rememberSaveable { mutableStateOf(services.first().id) }
    var open by rememberSaveable { mutableStateOf(false) }
    val chosen = services.firstOrNull { it.id == chosenId } ?: services.first()
    SpaCard(modifier.fillMaxWidth(), padding = PaddingValues(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(Res.string.home_quick_title),
                style = SpaTheme.textStyles.screenTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                OutlinedTextField(
                    value = chosen.name,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(Res.string.home_quick_service)) },
                    supportingText = {
                        Text(stringResource(Res.string.home_treatment_meta, chosen.minutes, formatPrice(chosen.priceVnd)))
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                    textStyle = SpaTheme.textStyles.fieldText,
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    services.forEach { service ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(service.name, style = SpaTheme.textStyles.body, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        stringResource(Res.string.home_treatment_meta, service.minutes, formatPrice(service.priceVnd)),
                                        style = SpaTheme.textStyles.label,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                chosenId = service.id
                                open = false
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            SpaPrimaryButton(text = stringResource(Res.string.home_quick_action), onClick = { onBookService(chosen.id) })
            Text(
                stringResource(Res.string.home_quick_note),
                style = SpaTheme.textStyles.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Facts(state: HomeState) {
    val minutes = state.services.map { it.minutes }
    val cheapest = state.services.minOf { it.priceVnd }
    val facts = buildList {
        add(state.services.size.toString() to stringResource(Res.string.home_stat_treatments))
        add(
            stringResource(
                Res.string.home_stat_minutes,
                minutes.min(),
                minutes.max(),
            ) to stringResource(Res.string.home_stat_minutes_label),
        )
        add(formatPrice(cheapest) to stringResource(Res.string.home_stat_price_label))
        if (state.liveOffers > 0) add(state.liveOffers.toString() to stringResource(Res.string.home_stat_offers))
    }
    Section(field = true) {
        BoxWithConstraints {
            val perRow = if (maxWidth < 560.dp) 2 else facts.size
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                facts.chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { (figure, label) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    figure,
                                    style = SpaTheme.textStyles.displayTitle.copy(fontSize = 32.sp, lineHeight = 36.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    label,
                                    style = SpaTheme.textStyles.body,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHead(title: String, lead: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            style = SpaTheme.textStyles.displayTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }.revealOnScroll(),
        )
        Text(
            lead,
            style = SpaTheme.textStyles.bodyLoose,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 620.dp).padding(top = 12.dp),
        )
    }
}

@Composable
private fun Treatments(state: HomeState, onBookService: (String) -> Unit) {
    Section {
        SectionHead(stringResource(Res.string.home_treatments_title), stringResource(Res.string.home_treatments_lead))
        TwoColumns(state.services) { service ->
            TreatmentCard(service, onBook = if (state.canBook) ({ onBookService(service.id) }) else null)
        }
        Text(
            stringResource(Res.string.home_treatments_note),
            style = SpaTheme.textStyles.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun <T> TwoColumns(items: List<T>, card: @Composable (T) -> Unit) {
    val columns = if (SpaWindow.size.isCompact) 1 else 2
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        staggeredColumns(items, columns).forEach { column ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { column.forEach { card(it) } }
        }
    }
}

@Composable
private fun TreatmentCard(service: Service, onBook: (() -> Unit)?) {
    SpaCard(Modifier.fillMaxWidth().revealOnScroll(), onClick = onBook, padding = PaddingValues(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SpaImageTile(Modifier.size(56.dp), shape = CircleShape, photo = SpaPhotos.treatment(service.id))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    service.name,
                    style = SpaTheme.textStyles.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    service.includes.ifBlank { service.category.localizedLabel() },
                    style = SpaTheme.textStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onBook != null) SpaIconImage(SpaIcon.ChevronRight, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Prices(state: HomeState) {
    Section(field = true) {
        SectionHead(stringResource(Res.string.home_prices_title), stringResource(Res.string.home_prices_lead))
        state.services.groupBy { it.category }.forEach { (category, services) ->
            PriceGroup(category.localizedLabel()) {
                services.forEach { service ->
                    PriceRow(
                        name = service.name,
                        detail = stringResource(Res.string.home_treatment_meta, service.minutes, service.includes),
                        price = formatPrice(service.priceVnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceGroup(title: String, rows: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            style = SpaTheme.textStyles.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                heading()
            },
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary))
        rows()
    }
}

@Composable
private fun PriceRow(name: String, detail: String, price: String) {
    SpaCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = SpaTheme.textStyles.cardTitle, color = MaterialTheme.colorScheme.onSurface)
                if (detail.isNotBlank()) {
                    Text(detail, style = SpaTheme.textStyles.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(price, style = SpaTheme.textStyles.cardTitle, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Shelf(products: List<Product>) {
    Section {
        SectionHead(
            stringResource(Res.string.home_products),
            stringResource(if (products.isEmpty()) Res.string.home_products_empty else Res.string.home_products_note),
        )
        TwoColumns(products) { product ->
            PriceRow(
                name = product.name,
                detail = listOf(product.brand, product.size, product.description).filter { it.isNotBlank() }.joinToString(" · "),
                price = formatPrice(product.priceVnd),
            )
        }
    }
}

@Composable
private fun OffersBanner(count: Int, onOpen: () -> Unit) {
    SpaCard(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, onClick = onOpen) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SpaIconImage(SpaIcon.Tag, size = 24.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text = stringResource(Res.string.home_offers, count),
                style = SpaTheme.textStyles.body,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(Res.string.home_offers_action), style = SpaTheme.textStyles.tag, color = MaterialTheme.colorScheme.primary)
            Box(Modifier.width(4.dp))
            SpaIconImage(SpaIcon.ChevronRight, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
