package com.tana.spa.core.ui

import com.tana.spa.shared.helpers.formatVnd

fun formatPrice(vnd: Int): String = formatVnd(vnd.toLong())
