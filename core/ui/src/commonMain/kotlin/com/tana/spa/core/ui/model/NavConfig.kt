package com.tana.spa.core.ui.model

import org.jetbrains.compose.resources.DrawableResource

data class NavConfig(val route: String, val label: String, val icon: DrawableResource? = null)
