package com.tana.spa.common

import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.feature.assistant.AssistantEntry
import com.tana.spa.feature.assistant.ConsentEntry
import com.tana.spa.feature.assistant.WelcomeEntry
import com.tana.spa.feature.auth.AuthEntry
import com.tana.spa.feature.auth.SignUpEntry
import com.tana.spa.feature.auth.VerifyPhoneEntry
import com.tana.spa.feature.explore.ExploreEntry
import com.tana.spa.feature.feed.FeedEntry
import com.tana.spa.feature.likes.LikesEntry
import com.tana.spa.feature.newpost.NewPostEntry
import com.tana.spa.feature.notifications.NotificationsEntry
import com.tana.spa.feature.profile.ProfileEntry

/** Aggregates every feature module's navigable entry for the app to render. */
object FeaturesRegistry {
    val entries: List<FeatureEntry> = listOf(
        FeedEntry,
        ExploreEntry,
        NewPostEntry,
        LikesEntry,
        ProfileEntry,
        NotificationsEntry,
        AuthEntry,
        SignUpEntry,
        VerifyPhoneEntry,
        WelcomeEntry,
        ConsentEntry,
        AssistantEntry,
    )
}
