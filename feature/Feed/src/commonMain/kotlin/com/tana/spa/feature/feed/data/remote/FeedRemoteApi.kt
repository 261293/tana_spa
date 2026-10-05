package com.tana.spa.feature.feed.data.remote

import com.tana.spa.feature.feed.data.remote.dto.feed.response.FeedResponseDto

interface FeedRemoteApi {
    suspend fun fetchFeed(): List<FeedResponseDto>
}
