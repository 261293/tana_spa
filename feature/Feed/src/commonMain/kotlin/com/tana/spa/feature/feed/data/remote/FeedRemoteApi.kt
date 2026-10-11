package com.tana.spa.feature.feed.data.remote

import com.tana.spa.feature.feed.data.remote.dto.AppointmentDto
import com.tana.spa.feature.feed.data.remote.dto.BannerDto
import com.tana.spa.feature.feed.data.remote.dto.ProductDto
import com.tana.spa.feature.feed.data.remote.dto.ServiceCategoryDto
import com.tana.spa.feature.feed.data.remote.dto.ServiceDto

/** One call per home section, so one failing section never blanks the others. */
interface FeedRemoteApi {
    suspend fun banners(): List<BannerDto>
    suspend fun categories(): List<ServiceCategoryDto>
    suspend fun featuredServices(): List<ServiceDto>
    suspend fun products(): List<ProductDto>
    suspend fun appointments(): List<AppointmentDto>
}
