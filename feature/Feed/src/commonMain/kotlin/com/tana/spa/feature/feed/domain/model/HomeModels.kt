package com.tana.spa.feature.feed.domain.model

enum class HomeSection { BANNERS, CATEGORIES, FEATURED_SERVICES, PRODUCTS, APPOINTMENTS }

enum class BannerLink { NONE, SERVICE, PRODUCT, VOUCHER }

data class Banner(val id: String, val title: String, val link: BannerLink)

data class ServiceCategory(val id: String, val name: String)

data class ServiceSummary(val id: String, val name: String, val priceVnd: Long, val durationMinutes: Int, val isCombo: Boolean)

data class ProductSummary(val id: String, val name: String, val priceVnd: Long)

data class Appointment(val id: String, val serviceName: String, val startsAtMillis: Long)

data class HomeContent(
    val banners: List<Banner> = emptyList(),
    val categories: List<ServiceCategory> = emptyList(),
    val featuredServices: List<ServiceSummary> = emptyList(),
    val products: List<ProductSummary> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
)
