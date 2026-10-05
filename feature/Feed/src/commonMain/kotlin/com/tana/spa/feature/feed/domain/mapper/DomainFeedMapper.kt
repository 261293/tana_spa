package com.tana.spa.feature.feed.domain.mapper

import com.tana.spa.feature.feed.data.remote.dto.feed.response.FeedResponseDto
import com.tana.spa.feature.feed.domain.model.DomainFeed

fun FeedResponseDto.toDomain(): DomainFeed = DomainFeed(
    id = id.toString(),
    author = "user_$id",
    caption = title,
)
