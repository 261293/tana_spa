package com.tana.spa.feature.feed.presentation.manipulator

import com.tana.spa.feature.feed.presentation.data.UiFeed

data class FeedState(
    val isRefreshing: Boolean = true,
    val items: List<UiFeed> = emptyList(),
    /** Only set when a refresh failed and there is nothing cached to show. */
    val error: String? = null,
)

/** One-shot outputs that must not be replayed on recomposition or rotation. */
sealed interface FeedEffect {
    data class ShowMessage(val message: String) : FeedEffect
}
