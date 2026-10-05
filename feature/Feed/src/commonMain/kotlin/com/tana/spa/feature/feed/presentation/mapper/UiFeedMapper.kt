package com.tana.spa.feature.feed.presentation.mapper

import com.tana.spa.feature.feed.domain.model.DomainFeed
import com.tana.spa.feature.feed.presentation.data.UiFeed

fun DomainFeed.toUi(): UiFeed = UiFeed(
    id = id,
    author = author,
    caption = caption,
)
