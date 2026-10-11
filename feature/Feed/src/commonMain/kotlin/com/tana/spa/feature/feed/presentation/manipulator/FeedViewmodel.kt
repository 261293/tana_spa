package com.tana.spa.feature.feed.presentation.manipulator

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.feed.domain.HomeUserName
import com.tana.spa.feature.feed.domain.model.HomeContent
import com.tana.spa.feature.feed.domain.model.HomeSection
import com.tana.spa.feature.feed.domain.repository.FeedRepository
import com.tana.spa.feature.feed.presentation.mapper.toUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class FeedViewmodel(private val repository: FeedRepository, userName: HomeUserName) :
    MviViewModel<FeedState, FeedEvent, FeedEffect>(FeedState(greetingName = userName.current())) {

    /** Null until a refresh has finished, which is what separates "still loading" from "nothing to show". */
    private val failedSections = MutableStateFlow<Set<HomeSection>?>(null)

    init {
        combine(repository.home, failedSections) { content, failed -> content to failed }
            .onEach { (content, failed) -> setState { content.applyTo(this, failed) } }
            .launchIn(viewModelScope)
        refresh()
    }

    override fun handleEvent(event: FeedEvent) {
        when (event) {
            FeedEvent.Refresh -> refresh()
            FeedEvent.AssistantClicked -> sendEffect(FeedEffect.OpenAssistant)
            FeedEvent.ItemClicked -> sendEffect(FeedEffect.ShowComingSoon)
        }
    }

    private fun refresh() {
        failedSections.value = null
        viewModelScope.launch {
            val failed = repository.refresh()
            failedSections.value = failed
            AppLog.i(logTag) { "home refreshed, failed sections=${failed.map { it.name }}" }
        }
    }

    private fun HomeContent.applyTo(state: FeedState, failed: Set<HomeSection>?) = state.copy(
        appointments = section(HomeSection.APPOINTMENTS, appointments.map { it.toUi() }, failed),
        banners = section(HomeSection.BANNERS, banners.map { it.toUi() }, failed),
        categories = section(HomeSection.CATEGORIES, categories.map { it.toUi() }, failed),
        featuredServices = section(HomeSection.FEATURED_SERVICES, featuredServices.map { it.toUi() }, failed),
        products = section(HomeSection.PRODUCTS, products.map { it.toUi() }, failed),
    )

    private fun <T> section(section: HomeSection, items: List<T>, failed: Set<HomeSection>?): SectionState<T> = when {
        items.isNotEmpty() -> SectionState.Ready(items)
        failed == null -> SectionState.Loading
        section in failed -> SectionState.Failed
        else -> SectionState.Ready(emptyList())
    }
}
