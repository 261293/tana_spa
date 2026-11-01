package com.tana.spa.shared.helpers

private const val GROUP_SIZE = 3

const val VND_PER_USD = 25_000L

fun usdToVnd(usd: Int): Long = usd * VND_PER_USD

/** `1200000` becomes `1.200.000đ`. Hand-rolled because `NumberFormat` does not exist on wasm. */
fun formatVnd(amount: Long): String {
    val grouped = amount.toString().removePrefix("-").reversed().chunked(GROUP_SIZE).joinToString(".").reversed()
    val sign = if (amount < 0) "-" else ""
    return "$sign${grouped}đ"
}
