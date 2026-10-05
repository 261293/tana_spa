package com.tana.spa.feature.feed.presentation.manipulator

sealed interface FeedEvent {
    data object Refresh : FeedEvent
}
