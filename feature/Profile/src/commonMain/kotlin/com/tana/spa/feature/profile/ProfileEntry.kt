package com.tana.spa.feature.profile

import androidx.compose.runtime.Composable
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes

object ProfileEntry : FeatureEntry {
    override val route: String = Routes.PROFILE

    @Composable
    override fun Content(navigator: Navigator) = ProfileScreen()
}
