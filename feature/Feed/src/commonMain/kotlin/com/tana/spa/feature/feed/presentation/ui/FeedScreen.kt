package com.tana.spa.feature.feed.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaFloatingActionButton
import com.tana.spa.feature.feed.presentation.manipulator.FeedEffect
import com.tana.spa.feature.feed.presentation.manipulator.FeedEvent
import com.tana.spa.feature.feed.presentation.manipulator.FeedState
import com.tana.spa.feature.feed.resources.Res
import com.tana.spa.feature.feed.resources.home_assistant_fab
import com.tana.spa.feature.feed.resources.home_coming_soon
import com.tana.spa.feature.feed.resources.home_greeting
import com.tana.spa.feature.feed.resources.home_greeting_named
import com.tana.spa.feature.feed.resources.home_section_appointments
import com.tana.spa.feature.feed.resources.home_section_categories
import com.tana.spa.feature.feed.resources.home_section_products
import com.tana.spa.feature.feed.resources.home_section_services
import com.tana.spa.feature.feed.resources.home_subtitle
import com.tana.spa.feature.feed.resources.ic_sparkle
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val CATEGORY_COLUMNS = 3

/** Stateless: renders [state], reports intents via [onEvent], routes one-shot [effects]. */
@Composable
fun FeedScreen(state: FeedState, effects: Flow<FeedEffect>, onEvent: (FeedEvent) -> Unit, onOpenAssistant: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val openAssistant by rememberUpdatedState(onOpenAssistant)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                FeedEffect.OpenAssistant -> openAssistant()
                FeedEffect.ShowComingSoon -> snackbar.showSnackbar(getString(Res.string.home_coming_soon))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        HomeBody(state, onEvent)
        SpaFloatingActionButton(
            icon = painterResource(Res.drawable.ic_sparkle),
            contentDescription = stringResource(Res.string.home_assistant_fab),
            onClick = { onEvent(FeedEvent.AssistantClicked) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(ScreenPadding),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun HomeBody(state: FeedState, onEvent: (FeedEvent) -> Unit) {
    val onRetry = { onEvent(FeedEvent.Refresh) }
    val onItem = { onEvent(FeedEvent.ItemClicked) }
    val rowPadding = PaddingValues(horizontal = ScreenPadding)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { Header(state.greetingName) }

        item {
            AssistantCard(
                onClick = { onEvent(FeedEvent.AssistantClicked) },
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
        }

        if (!state.appointments.isEmptyReady()) {
            item {
                HomeSection(stringResource(Res.string.home_section_appointments), state.appointments, onRetry, 72.dp) { appointments ->
                    Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        appointments.forEach { AppointmentCard(it, onClick = onItem) }
                    }
                }
            }
        }

        if (!state.banners.isEmptyReady()) {
            item {
                HomeSection(null, state.banners, onRetry, 144.dp) { banners ->
                    BannerPager(banners, onClick = onItem)
                }
            }
        }

        if (!state.categories.isEmptyReady()) {
            item {
                HomeSection(stringResource(Res.string.home_section_categories), state.categories, onRetry, 160.dp) { categories ->
                    Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.chunked(CATEGORY_COLUMNS).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { CategoryTile(it, onClick = onItem, modifier = Modifier.weight(1f)) }
                                repeat(CATEGORY_COLUMNS - rowItems.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }

        if (!state.featuredServices.isEmptyReady()) {
            item {
                HomeSection(stringResource(Res.string.home_section_services), state.featuredServices, onRetry, 220.dp) { services ->
                    LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(services, key = { it.id }) { ServiceCard(it, onClick = onItem) }
                    }
                }
            }
        }

        if (!state.products.isEmptyReady()) {
            item {
                HomeSection(stringResource(Res.string.home_section_products), state.products, onRetry, 200.dp) { products ->
                    LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(products, key = { it.id }) { ProductCard(it, onClick = onItem) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(name: String?) {
    Column(
        modifier = Modifier.padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (name.isNullOrBlank()) {
                stringResource(Res.string.home_greeting)
            } else {
                stringResource(Res.string.home_greeting_named, name)
            },
            style = SpaTheme.textStyles.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.home_subtitle),
            style = SpaTheme.textStyles.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
