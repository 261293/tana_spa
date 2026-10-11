package com.tana.spa.feature.feed.data.remote.source

import com.tana.spa.core.network.NetworkClient
import com.tana.spa.feature.feed.data.remote.FeedRemoteApi
import com.tana.spa.feature.feed.data.remote.dto.AppointmentDto
import com.tana.spa.feature.feed.data.remote.dto.BannerDto
import com.tana.spa.feature.feed.data.remote.dto.ProductDto
import com.tana.spa.feature.feed.data.remote.dto.ServiceCategoryDto
import com.tana.spa.feature.feed.data.remote.dto.ServiceDto

class FeedRemoteApiImpl(@Suppress("unused") private val networkClient: NetworkClient) : FeedRemoteApi {

    override suspend fun banners(): List<BannerDto> = listOf(
        BannerDto(id = "b1", title = "Ưu đãi chăm sóc da giảm 20%", linkType = "service"),
        BannerDto(id = "b2", title = "Combo thư giãn cuối tuần", linkType = "service"),
        BannerDto(id = "b3", title = "Tặng voucher cho khách mới", linkType = "voucher"),
        BannerDto(id = "b4", title = "Massage đá nóng giảm 15% giờ vàng", linkType = "service"),
        BannerDto(id = "b5", title = "Gói chăm sóc da cho hai người", linkType = "service"),
        BannerDto(id = "b6", title = "Mua serum vitamin C, tặng mặt nạ dưỡng ẩm", linkType = "product"),
    )

    override suspend fun categories(): List<ServiceCategoryDto> = listOf(
        ServiceCategoryDto(id = "c1", name = "Chăm sóc da"),
        ServiceCategoryDto(id = "c2", name = "Massage"),
        ServiceCategoryDto(id = "c3", name = "Gội đầu dưỡng sinh"),
        ServiceCategoryDto(id = "c4", name = "Nail"),
        ServiceCategoryDto(id = "c5", name = "Triệt lông"),
        ServiceCategoryDto(id = "c6", name = "Xông hơi"),
    )

    override suspend fun featuredServices(): List<ServiceDto> = listOf(
        ServiceDto(id = "s1", name = "Chăm sóc da cơ bản", priceVnd = 450_000, durationMinutes = 60),
        ServiceDto(id = "s2", name = "Massage body đá nóng", priceVnd = 650_000, durationMinutes = 90),
        ServiceDto(id = "s3", name = "Combo thư giãn toàn thân", priceVnd = 1_200_000, durationMinutes = 150, isCombo = true),
    )

    override suspend fun products(): List<ProductDto> = listOf(
        ProductDto(id = "p1", name = "Sữa rửa mặt dịu nhẹ", priceVnd = 220_000),
        ProductDto(id = "p2", name = "Serum vitamin C", priceVnd = 480_000),
        ProductDto(id = "p3", name = "Kem chống nắng SPF50", priceVnd = 350_000),
    )

    // There is no bookings API yet, so this stays empty and the section stays hidden.
    override suspend fun appointments(): List<AppointmentDto> = emptyList()
}
