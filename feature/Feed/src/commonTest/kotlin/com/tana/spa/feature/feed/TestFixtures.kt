package com.tana.spa.feature.feed

import com.tana.spa.feature.feed.data.remote.dto.AppointmentDto
import com.tana.spa.feature.feed.data.remote.dto.BannerDto
import com.tana.spa.feature.feed.data.remote.dto.ProductDto
import com.tana.spa.feature.feed.data.remote.dto.ServiceCategoryDto
import com.tana.spa.feature.feed.data.remote.dto.ServiceDto
import com.tana.spa.feature.feed.domain.model.Appointment
import com.tana.spa.feature.feed.domain.model.Banner
import com.tana.spa.feature.feed.domain.model.BannerLink
import com.tana.spa.feature.feed.domain.model.HomeContent
import com.tana.spa.feature.feed.domain.model.ProductSummary
import com.tana.spa.feature.feed.domain.model.ServiceCategory
import com.tana.spa.feature.feed.domain.model.ServiceSummary

object TestFixtures {
    val bannerDto = BannerDto(id = "b1", title = "Sale", linkType = "service")
    val categoryDto = ServiceCategoryDto(id = "c1", name = "Skin")
    val serviceDto = ServiceDto(id = "s1", name = "Facial", priceVnd = 450_000, durationMinutes = 60)
    val productDto = ProductDto(id = "p1", name = "Serum", priceVnd = 480_000)
    val appointmentDto = AppointmentDto(id = "a1", serviceName = "Facial", startsAtMillis = 1_000L)

    val banner = Banner(id = "b1", title = "Sale", link = BannerLink.SERVICE)
    val category = ServiceCategory(id = "c1", name = "Skin")
    val service = ServiceSummary(id = "s1", name = "Facial", priceVnd = 450_000, durationMinutes = 60, isCombo = false)
    val product = ProductSummary(id = "p1", name = "Serum", priceVnd = 480_000)
    val appointment = Appointment(id = "a1", serviceName = "Facial", startsAtMillis = 1_000L)

    val content = HomeContent(
        banners = listOf(banner),
        categories = listOf(category),
        featuredServices = listOf(service),
        products = listOf(product),
        appointments = listOf(appointment),
    )
}
