package com.tana.spa.feature.booking.domain

/** A retail product on the spa's shelf, at its cheapest size; [size] and [brand] may be blank. */
data class Product(
    val id: String,
    val name: String,
    val brand: String,
    val description: String,
    val imageUrl: String?,
    val priceVnd: Int,
    val size: String,
)
