package com.tana.spa.feature.newpost

import androidx.compose.runtime.Composable
import com.tana.spa.core.navigation.FeatureEntry
import com.tana.spa.core.navigation.Navigator
import com.tana.spa.core.navigation.Routes

object NewPostEntry : FeatureEntry {
    override val route: String = Routes.NEW_POST

    @Composable
    override fun Content(navigator: Navigator) = NewPostScreen()
}
