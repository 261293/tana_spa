package com.tana.spa.feature.feed.data.remote.dto.feed.response

import kotlinx.serialization.Serializable

@Serializable
data class FeedResponseDto(val id: Int, val title: String = "", val body: String = "")
