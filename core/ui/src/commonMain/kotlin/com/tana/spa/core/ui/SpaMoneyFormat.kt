package com.tana.spa.core.ui

import com.tana.spa.shared.helpers.formatVnd
import com.tana.spa.shared.helpers.usdToVnd

fun formatPrice(usd: Int): String = if (vietnamese) formatVnd(usdToVnd(usd)) else "\$$usd"
