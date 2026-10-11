package com.tana.spa.feature.feed.presentation.mapper

import com.tana.spa.feature.feed.domain.model.Appointment
import com.tana.spa.feature.feed.domain.model.Banner
import com.tana.spa.feature.feed.domain.model.ProductSummary
import com.tana.spa.feature.feed.domain.model.ServiceCategory
import com.tana.spa.feature.feed.domain.model.ServiceSummary
import com.tana.spa.feature.feed.presentation.data.UiAppointment
import com.tana.spa.feature.feed.presentation.data.UiBanner
import com.tana.spa.feature.feed.presentation.data.UiCategory
import com.tana.spa.feature.feed.presentation.data.UiProduct
import com.tana.spa.feature.feed.presentation.data.UiService
import com.tana.spa.shared.helpers.formatVnd

fun Banner.toUi() = UiBanner(id = id, title = title)

fun ServiceCategory.toUi() = UiCategory(id = id, name = name)

fun ServiceSummary.toUi() = UiService(
    id = id,
    name = name,
    priceLabel = formatVnd(priceVnd),
    durationMinutes = durationMinutes,
    isCombo = isCombo,
)

fun ProductSummary.toUi() = UiProduct(id = id, name = name, priceLabel = formatVnd(priceVnd))

fun Appointment.toUi() = UiAppointment(id = id, serviceName = serviceName, startsAtMillis = startsAtMillis)
