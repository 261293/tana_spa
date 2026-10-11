package com.tana.spa.feature.feed.presentation.data

data class UiBanner(val id: String, val title: String)

data class UiCategory(val id: String, val name: String)

data class UiService(val id: String, val name: String, val priceLabel: String, val durationMinutes: Int, val isCombo: Boolean)

data class UiProduct(val id: String, val name: String, val priceLabel: String)

data class UiAppointment(val id: String, val serviceName: String, val startsAtMillis: Long)
