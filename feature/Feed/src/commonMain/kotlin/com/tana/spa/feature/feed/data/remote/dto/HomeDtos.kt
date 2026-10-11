package com.tana.spa.feature.feed.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class BannerDto(val id: String, val title: String, val linkType: String = "none")

@Serializable
data class ServiceCategoryDto(val id: String, val name: String)

@Serializable
data class ServiceDto(val id: String, val name: String, val priceVnd: Long, val durationMinutes: Int, val isCombo: Boolean = false)

@Serializable
data class ProductDto(val id: String, val name: String, val priceVnd: Long)

@Serializable
data class AppointmentDto(val id: String, val serviceName: String, val startsAtMillis: Long)
