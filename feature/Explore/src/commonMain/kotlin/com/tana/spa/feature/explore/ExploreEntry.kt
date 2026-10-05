package com.tana.spa.feature.explore

import androidx.compose.runtime.Composable
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes

object ExploreEntry : FeatureEntry {
    override val route: String = Routes.EXPLORE

    @Composable
    override fun Content(navigator: Navigator) = ExploreScreen()
}
