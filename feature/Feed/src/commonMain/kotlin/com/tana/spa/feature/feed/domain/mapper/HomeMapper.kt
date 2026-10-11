package com.tana.spa.feature.feed.domain.mapper

import com.tana.spa.feature.feed.data.remote.dto.AppointmentDto
import com.tana.spa.feature.feed.data.remote.dto.BannerDto
import com.tana.spa.feature.feed.data.remote.dto.ProductDto
import com.tana.spa.feature.feed.data.remote.dto.ServiceCategoryDto
import com.tana.spa.feature.feed.data.remote.dto.ServiceDto
import com.tana.spa.feature.feed.domain.model.Appointment
import com.tana.spa.feature.feed.domain.model.Banner
import com.tana.spa.feature.feed.domain.model.BannerLink
import com.tana.spa.feature.feed.domain.model.ProductSummary
import com.tana.spa.feature.feed.domain.model.ServiceCategory
import com.tana.spa.feature.feed.domain.model.ServiceSummary

fun BannerDto.toDomain() = Banner(id = id, title = title, link = linkType.toBannerLink())

fun ServiceCategoryDto.toDomain() = ServiceCategory(id = id, name = name)

fun ServiceDto.toDomain() = ServiceSummary(
    id = id,
    name = name,
    priceVnd = priceVnd,
    durationMinutes = durationMinutes,
    isCombo = isCombo,
)

fun ProductDto.toDomain() = ProductSummary(id = id, name = name, priceVnd = priceVnd)

fun AppointmentDto.toDomain() = Appointment(id = id, serviceName = serviceName, startsAtMillis = startsAtMillis)

private fun String.toBannerLink(): BannerLink = when (this) {
    "service" -> BannerLink.SERVICE
    "product" -> BannerLink.PRODUCT
    "voucher" -> BannerLink.VOUCHER
    else -> BannerLink.NONE
}
