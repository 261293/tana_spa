package com.tana.spa.core.ui.model

import com.tana.spa.core.ui.components.SpaIcon

/** One bottom-navigation destination. [badgeCount] of 0 hides the badge. */
data class NavConfig(val route: String, val label: String, val icon: SpaIcon, val badgeCount: Int = 0)
