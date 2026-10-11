package com.tana.spa.feature.feed.presentation.manipulator

import com.tana.spa.feature.feed.presentation.data.UiAppointment
import com.tana.spa.feature.feed.presentation.data.UiBanner
import com.tana.spa.feature.feed.presentation.data.UiCategory
import com.tana.spa.feature.feed.presentation.data.UiProduct
import com.tana.spa.feature.feed.presentation.data.UiService

/** What one block of the home screen shows. An empty [Ready] is hidden, not drawn as a blank card. */
sealed interface SectionState<out T> {
    data object Loading : SectionState<Nothing>
    data class Ready<T>(val items: List<T>) : SectionState<T>
    data object Failed : SectionState<Nothing>
}

data class FeedState(
    val greetingName: String? = null,
    val appointments: SectionState<UiAppointment> = SectionState.Loading,
    val banners: SectionState<UiBanner> = SectionState.Loading,
    val categories: SectionState<UiCategory> = SectionState.Loading,
    val featuredServices: SectionState<UiService> = SectionState.Loading,
    val products: SectionState<UiProduct> = SectionState.Loading,
)

/** One-shot outputs that must not be replayed on recomposition or rotation. */
sealed interface FeedEffect {
    data object OpenAssistant : FeedEffect
    data object ShowComingSoon : FeedEffect
}
