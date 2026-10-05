package com.tana.spa.feature.likes

import androidx.compose.runtime.Composable
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes

object LikesEntry : FeatureEntry {
    override val route: String = Routes.LIKES

    @Composable
    override fun Content(navigator: Navigator) = LikesScreen()
}
