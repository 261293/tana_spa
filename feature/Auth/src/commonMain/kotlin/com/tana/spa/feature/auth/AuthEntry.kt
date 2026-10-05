package com.tana.spa.feature.auth

import androidx.compose.runtime.Composable
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes

object AuthEntry : FeatureEntry {
    override val route: String = Routes.AUTH

    @Composable
    override fun Content(navigator: Navigator) = AuthScreen()
}
