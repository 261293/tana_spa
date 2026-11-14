package com.tana.spa.feature.booking.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaPhotos
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.SpaWindow
import com.tana.spa.core.ui.components.SpaDateBlock
import com.tana.spa.core.ui.components.SpaEmptyBlock
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaImageTile
import com.tana.spa.core.ui.components.SpaListRow
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaPanel
import com.tana.spa.core.ui.components.SpaPrice
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaRowGroup
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaSkeleton
import com.tana.spa.core.ui.components.SpaTextButton
import com.tana.spa.core.ui.formatHour
import com.tana.spa.core.ui.formatPrice
import com.tana.spa.core.ui.localizedLabel
import com.tana.spa.core.ui.monthShort
import com.tana.spa.core.ui.rememberSpaScrollState
import com.tana.spa.core.ui.revealOnScroll
import com.tana.spa.core.ui.weekdayShort
import com.tana.spa.feature.booking.domain.GuestBooking
import com.tana.spa.feature.booking.domain.Product
import com.tana.spa.feature.booking.presentation.HomeEvent
import com.tana.spa.feature.booking.presentation.HomeState
import com.tana.spa.feature.booking.resources.Res
import com.tana.spa.feature.booking.resources.home_failed
import com.tana.spa.feature.booking.resources.home_next_meta
import com.tana.spa.feature.booking.resources.home_next_open
import com.tana.spa.feature.booking.resources.home_next_title
import com.tana.spa.feature.booking.resources.home_offers
import com.tana.spa.feature.booking.resources.home_offers_action
import com.tana.spa.feature.booking.resources.home_products
import com.tana.spa.feature.booking.resources.home_products_empty
import com.tana.spa.feature.booking.resources.home_products_note
import com.tana.spa.feature.booking.resources.home_quick_action
import com.tana.spa.feature.booking.resources.home_quick_note
import com.tana.spa.feature.booking.resources.home_quick_service
import com.tana.spa.feature.booking.resources.home_quick_title
import com.tana.spa.feature.booking.resources.home_retry
import com.tana.spa.feature.booking.resources.home_subtitle
import com.tana.spa.feature.booking.resources.home_subtitle_staff
import com.tana.spa.feature.booking.resources.home_title
import com.tana.spa.feature.booking.resources.home_treatment_meta
import com.tana.spa.feature.booking.resources.home_treatments_lead
import com.tana.spa.feature.booking.resources.home_treatments_title
import com.tana.spa.shared.spa.Service
import com.tana.spa.shared.spa.SpaDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val PhotoHeight = 300.dp
private val ThumbSize = 48.dp

@Composable
fun HomeScreen(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    onBookService: (String) -> Unit,
    onOpenOffers: () -> Unit,
    onOpenVisit: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberSpaScrollState())) {
        SpaPage(maxWidth = SpaPageWidth.Full, verticalArrangement = Arrangement.spacedBy(40.dp), top = 24.dp) {
            Opening(state, onBookService, onOpenVisit)
            when {
                state.loading -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { SpaSkeleton(64.dp) } }

                state.failed -> SpaEmptyBlock(stringResource(Res.string.home_failed)) {
                    SpaTextButton(stringResource(Res.string.home_retry), onClick = { onEvent(HomeEvent.Retry) })
                }

                else -> {
                    Menu(state.services, onBook = if (state.canBook) onBookService else null)
                    Shelf(state.products)
                    if (state.liveOffers > 0) Offers(state.liveOffers, onOpenOffers)
                }
            }
        }
    }
}

@Composable
private fun Opening(state: HomeState, onBookService: (String) -> Unit, onOpenVisit: (String) -> Unit) {
    val wide = !SpaWindow.size.isCompact
    val action: (@Composable () -> Unit)? = when {
        !state.canBook || state.loading -> null
        state.nextVisit != null -> ({ NextVisit(state.nextVisit, onOpenVisit) })
        state.services.isNotEmpty() -> ({ QuickBook(state.services, onBookService) })
        else -> null
    }
    if (wide) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Greeting(state.canBook)
                action?.invoke()
            }
            Image(
                painter = painterResource(SpaPhotos.hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.weight(
                    1f,
                ).fillMaxHeight().heightIn(min = PhotoHeight).withoutIntrinsicHeight().clip(MaterialTheme.shapes.medium),
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Greeting(state.canBook)
            action?.invoke()
        }
    }
}

private fun Modifier.withoutIntrinsicHeight(): Modifier = this.then(
    object : LayoutModifier {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }

        override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int = 0

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int = 0
    },
)

@Composable
private fun Greeting(canBook: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.home_title),
            style = SpaTheme.textStyles.heroTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(if (canBook) Res.string.home_subtitle else Res.string.home_subtitle_staff),
            style = SpaTheme.textStyles.bodyLoose,
            color = SpaTheme.extraColors.inkSoft,
        )
    }
}

@Composable
private fun NextVisit(visit: GuestBooking, onOpenVisit: (String) -> Unit) {
    val date = SpaDate.fromEpochDay(visit.startDayEpochDay)
    SpaPanel(title = stringResource(Res.string.home_next_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SpaDateBlock(date.weekdayShort(), date.day.toString(), date.monthShort())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(visit.serviceName, style = SpaTheme.textStyles.body, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(Res.string.home_next_meta, formatHour(visit.startHour), visit.minutes),
                        style = SpaTheme.textStyles.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BookingStatusPill(visit.status)
                }
            }
            SpaSecondaryButton(stringResource(Res.string.home_next_open), onClick = { onOpenVisit(visit.id) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickBook(services: List<Service>, onBookService: (String) -> Unit) {
    var chosenId by rememberSaveable { mutableStateOf(services.first().id) }
    var open by rememberSaveable { mutableStateOf(false) }
    val chosen = services.firstOrNull { it.id == chosenId } ?: services.first()
    SpaPanel(title = stringResource(Res.string.home_quick_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                OutlinedTextField(
                    value = chosen.name,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(Res.string.home_quick_service)) },
                    supportingText = { Text(stringResource(Res.string.home_treatment_meta, chosen.minutes, formatPrice(chosen.priceVnd))) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                    textStyle = SpaTheme.textStyles.fieldText,
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        focusedLabelColor = MaterialTheme.colorScheme.secondary,
                    ),
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    services.forEach { service ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(service.name, style = SpaTheme.textStyles.body, fontWeight = FontWeight.Medium)
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
private fun SectionHeading(title: String, lead: String?) {
    Column(Modifier.revealOnScroll(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = SpaTheme.textStyles.screenTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        lead?.let { Text(it, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Menu(services: List<Service>, onBook: ((String) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionHeading(stringResource(Res.string.home_treatments_title), stringResource(Res.string.home_treatments_lead))
        val groups = services.groupBy { it.category }.toList()
        val columns = if (SpaWindow.size.isWide) 2 else 1
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            balancedColumns(groups, columns) { it.second.size + 1 }.forEach { column ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    column.forEach { (category, group) ->
                        SpaRowGroup(
                            title = category.localizedLabel(),
                            modifier = Modifier.revealOnScroll(),
                            rows = group.map { service -> { TreatmentRow(service, onBook) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TreatmentRow(service: Service, onBook: ((String) -> Unit)?) {
    SpaListRow(
        title = service.name,
        supporting = stringResource(
            Res.string.home_treatment_meta,
            service.minutes,
            service.includes.ifBlank {
                service.category.localizedLabel()
            },
        ),
        leading = { SpaImageTile(Modifier.size(ThumbSize), shape = MaterialTheme.shapes.small, photo = SpaPhotos.treatment(service.id)) },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SpaPrice(formatPrice(service.priceVnd))
                if (onBook != null) SpaIconImage(SpaIcon.ChevronRight, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        onClick = onBook?.let { { it(service.id) } },
    )
}

@Composable
private fun Shelf(products: List<Product>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(
            stringResource(Res.string.home_products),
            if (products.isEmpty()) null else stringResource(Res.string.home_products_note),
        )
        if (products.isEmpty()) {
            SpaEmptyBlock(stringResource(Res.string.home_products_empty))
        } else {
            SpaRowGroup(
                modifier = Modifier.revealOnScroll(),
                rows = products.map { product ->
                    {
                        SpaListRow(
                            title = product.name,
                            supporting = listOf(product.brand, product.size, product.description).filter {
                                it.isNotBlank()
                            }.joinToString(" · "),
                            trailing = { SpaPrice(formatPrice(product.priceVnd)) },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun Offers(count: Int, onOpen: () -> Unit) {
    SpaRowGroup(
        rows = listOf {
            SpaListRow(
                title = stringResource(Res.string.home_offers, count),
                leading = { SpaIconImage(SpaIcon.Tag, size = 20.dp, tint = MaterialTheme.colorScheme.secondary) },
                trailing = {
                    Text(
                        stringResource(Res.string.home_offers_action),
                        style = SpaTheme.textStyles.tag,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                },
                onClick = onOpen,
            )
        },
    )
}
