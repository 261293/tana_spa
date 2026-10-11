package com.tana.spa.feature.feed.presentation.manipulator

sealed interface FeedEvent {
    data object Refresh : FeedEvent
    data object AssistantClicked : FeedEvent

    /** A banner, category, service, product or appointment: none has a destination yet. */
    data object ItemClicked : FeedEvent
}
