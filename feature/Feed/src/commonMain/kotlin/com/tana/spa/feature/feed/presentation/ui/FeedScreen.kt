package com.tana.spa.feature.feed.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaCtaPanel
import com.tana.spa.core.ui.components.SpaDisplayTitle
import com.tana.spa.core.ui.components.SpaEyebrow
import com.tana.spa.core.ui.components.SpaFacts
import com.tana.spa.core.ui.components.SpaFilterChip
import com.tana.spa.core.ui.components.SpaFloatingActionButton
import com.tana.spa.core.ui.components.SpaImageTile
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaSectionHead
import com.tana.spa.feature.feed.presentation.manipulator.FeedEffect
import com.tana.spa.feature.feed.presentation.manipulator.FeedEvent
import com.tana.spa.feature.feed.presentation.manipulator.FeedState
import com.tana.spa.feature.feed.presentation.manipulator.SectionState
import com.tana.spa.feature.feed.resources.Res
import com.tana.spa.feature.feed.resources.home_assistant_fab
import com.tana.spa.feature.feed.resources.home_coming_soon
import com.tana.spa.feature.feed.resources.home_cta_action
import com.tana.spa.feature.feed.resources.home_cta_eyebrow
import com.tana.spa.feature.feed.resources.home_cta_title
import com.tana.spa.feature.feed.resources.home_fact_appointments
import com.tana.spa.feature.feed.resources.home_fact_categories
import com.tana.spa.feature.feed.resources.home_fact_services
import com.tana.spa.feature.feed.resources.home_greeting
import com.tana.spa.feature.feed.resources.home_greeting_named
import com.tana.spa.feature.feed.resources.home_hero_book
import com.tana.spa.feature.feed.resources.home_hero_explore
import com.tana.spa.feature.feed.resources.home_hero_title
import com.tana.spa.feature.feed.resources.home_hero_title_accent
import com.tana.spa.feature.feed.resources.home_products_eyebrow
import com.tana.spa.feature.feed.resources.home_section_products
import com.tana.spa.feature.feed.resources.home_section_services
import com.tana.spa.feature.feed.resources.home_see_all
import com.tana.spa.feature.feed.resources.home_subtitle
import com.tana.spa.feature.feed.resources.home_treatments_eyebrow
import com.tana.spa.feature.feed.resources.home_treatments_note
import com.tana.spa.feature.feed.resources.ic_sparkle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val HERO_MEDIA_RATIO = 3f / 2f
private const val TREATMENTS_ANCHOR = "treatments"

/** Stateless: renders [state], reports intents via [onEvent], routes one-shot [effects]. */
@Composable
fun FeedScreen(
    state: FeedState,
    effects: Flow<FeedEffect>,
    onEvent: (FeedEvent) -> Unit,
    onOpenAssistant: () -> Unit,
    onBook: () -> Unit,
    onExploreTreatments: () -> Unit,
) {
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
        HomeBody(state, onEvent, onBook, onExploreTreatments)
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
private fun HomeBody(state: FeedState, onEvent: (FeedEvent) -> Unit, onBook: () -> Unit, onExplore: () -> Unit) {
    val onRetry = { onEvent(FeedEvent.Refresh) }
    val onItem = { onEvent(FeedEvent.ItemClicked) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rowPadding = PaddingValues(horizontal = ScreenPadding)
    val sidePadding = Modifier.padding(horizontal = ScreenPadding)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Hero(
                greetingName = state.greetingName,
                facts = state.facts(),
                onBook = onBook,
                onExplore = onExplore,
                modifier = sidePadding,
            )
        }

        if (!state.appointments.isEmptyReady()) {
            item {
                HomeSection(null, state.appointments, onRetry, 96.dp) { appointments ->
                    appointments.firstOrNull()?.let { NextVisitCard(it, onClick = onItem, modifier = sidePadding) }
                }
            }
        }

        item(key = TREATMENTS_ANCHOR) {
            SpaSectionHead(
                eyebrow = stringResource(Res.string.home_treatments_eyebrow),
                title = stringResource(Res.string.home_section_services),
                note = stringResource(Res.string.home_treatments_note),
                modifier = sidePadding,
                action = {
                    TextButton(onClick = onExplore) {
                        Text(stringResource(Res.string.home_see_all), style = SpaTheme.textStyles.caption)
                    }
                },
            )
        }

        if (!state.categories.isEmptyReady()) {
            item {
                HomeSection(null, state.categories, onRetry, 48.dp) { categories ->
                    LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories, key = { it.id }) { category ->
                            SpaFilterChip(label = category.name, selected = false, onClick = onItem)
                        }
                    }
                }
            }
        }

        if (!state.featuredServices.isEmptyReady()) {
            item {
                HomeSection(null, state.featuredServices, onRetry, 280.dp) { services ->
                    Column(sidePadding, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        services.forEach { TreatmentCard(it, onClick = onItem) }
                    }
                }
            }
        }

        if (!state.products.isEmptyReady()) {
            item {
                HomeSection(null, state.products, onRetry, 200.dp) { products ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SpaSectionHead(
                            eyebrow = stringResource(Res.string.home_products_eyebrow),
                            title = stringResource(Res.string.home_section_products),
                            modifier = sidePadding,
                        )
                        LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(products, key = { it.id }) { ProductCard(it, onClick = onItem) }
                        }
                    }
                }
            }
        }

        item {
            SpaCtaPanel(
                eyebrow = stringResource(Res.string.home_cta_eyebrow),
                title = state.bannerTitle() ?: stringResource(Res.string.home_cta_title),
                actionLabel = stringResource(Res.string.home_cta_action),
                onAction = onBook,
                modifier = sidePadding,
            )
        }
    }
}

/** The treatments block is the fourth slot: hero, next visit, section head. */
private const val TREATMENTS_INDEX = 2

@Composable
private fun Hero(
    greetingName: String?,
    facts: List<Pair<String, String>>,
    onBook: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SpaEyebrow(
            if (greetingName.isNullOrBlank()) {
                stringResource(Res.string.home_greeting)
            } else {
                stringResource(Res.string.home_greeting_named, greetingName)
            },
        )
        SpaDisplayTitle(
            lead = stringResource(Res.string.home_hero_title),
            accent = stringResource(Res.string.home_hero_title_accent),
        )
        Text(
            text = stringResource(Res.string.home_subtitle),
            style = SpaTheme.textStyles.prompt,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpaPrimaryButton(stringResource(Res.string.home_hero_book), onBook, Modifier.weight(1f))
            SpaSecondaryButton(stringResource(Res.string.home_hero_explore), onExplore, Modifier.weight(1f))
        }
        if (facts.isNotEmpty()) {
            SpaFacts(facts, Modifier.padding(top = 4.dp))
        }
        SpaImageTile(
            modifier = Modifier.fillMaxWidth().aspectRatio(HERO_MEDIA_RATIO),
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

@Composable
private fun FeedState.facts(): List<Pair<String, String>> = buildList {
    countOf(featuredServices)?.let { add(stringResource(Res.string.home_fact_services) to it) }
    countOf(categories)?.let { add(stringResource(Res.string.home_fact_categories) to it) }
    countOf(appointments)?.let { add(stringResource(Res.string.home_fact_appointments) to it) }
}

private fun countOf(section: SectionState<*>): String? =
    (section as? SectionState.Ready)?.items?.takeIf { it.isNotEmpty() }?.size?.toString()

/** The design closes the page with one dark panel, so a promotion speaks through it. */
private fun FeedState.bannerTitle(): String? = (banners as? SectionState.Ready)?.items?.firstOrNull()?.title
