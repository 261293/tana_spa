package com.tana.spa.feature.appupdate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tana.spa.feature.appupdate.domain.StoreLauncher
import com.tana.spa.feature.appupdate.presentation.AppUpdateViewModel
import com.tana.spa.feature.appupdate.presentation.ui.UpdateModals
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The only place this feature reaches for Koin. Not a [com.tana.spa.core.navigation.FeatureEntry]:
 * it is an overlay above every route rather than a destination, so it is mounted once in `App`.
 */
object AppUpdateGate {

    @Composable
    fun Content() {
        val viewModel: AppUpdateViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val launcher: StoreLauncher = koinInject()
        UpdateModals(
            state = state,
            effects = viewModel.effects,
            onEvent = viewModel::onEvent,
            onOpenStore = launcher::open,
        )
    }
}
