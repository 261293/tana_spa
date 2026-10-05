package com.tana.spa.feature.feed.data.remote.source

import com.tana.spa.core.network.NetworkClient
import com.tana.spa.feature.feed.data.remote.FeedRemoteApi
import com.tana.spa.feature.feed.data.remote.dto.feed.response.FeedResponseDto

class FeedRemoteApiImpl(
    @Suppress("unused") private val networkClient: NetworkClient,
) : FeedRemoteApi {
    // Stub data; a full build would call networkClient.httpClient.get(Constants.BASE_URL/...).
    override suspend fun fetchFeed(): List<FeedResponseDto> = List(5) { i ->
        FeedResponseDto(id = i + 1, title = "Post ${i + 1}", body = "Body ${i + 1}")
    }
}
