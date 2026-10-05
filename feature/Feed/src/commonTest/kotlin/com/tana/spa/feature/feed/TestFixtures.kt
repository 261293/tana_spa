package com.tana.spa.feature.feed

import com.tana.spa.feature.feed.data.remote.dto.feed.response.FeedResponseDto
import com.tana.spa.feature.feed.domain.model.DomainFeed

object TestFixtures {
    val dto = FeedResponseDto(id = 1, title = "t", body = "b")
    val domain = DomainFeed(id = "1", author = "user_1", caption = "t")
}
