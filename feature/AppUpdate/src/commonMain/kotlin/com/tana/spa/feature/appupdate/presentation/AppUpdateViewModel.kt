package com.tana.spa.feature.appupdate.presentation

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.appupdate.data.AppUpdateRemoteApi
import com.tana.spa.feature.appupdate.domain.AppStoreLinks
import com.tana.spa.feature.appupdate.domain.InstalledAppVersion
import com.tana.spa.feature.appupdate.domain.updateVerdict
import com.tana.spa.shared.Platform
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AppUpdateViewModel(
    private val remote: AppUpdateRemoteApi,
    private val flags: FeatureFlags,
    private val installed: InstalledAppVersion,
    platform: Platform,
) : MviViewModel<AppUpdateState, AppUpdateEvent, AppUpdateEffect>(AppUpdateState()) {

    private val storeUrl = AppStoreLinks.forPlatform(platform.type)

    init {
        viewModelScope.launch { decide() }
    }

    override fun handleEvent(event: AppUpdateEvent) {
        when (event) {
            AppUpdateEvent.UpdateClicked -> sendEffect(AppUpdateEffect.OpenStore(storeUrl))
            AppUpdateEvent.Dismissed -> setState { copy(dismissed = true) }
        }
    }

    private suspend fun decide() {
        val current = installed.current()
        val verdict = updateVerdict(
            installed = current,
            config = fetchConfigOrNull(),
            gateEnabled = flags.isEnabled(FeatureFlag.APP_UPDATE_GATE),
            storeUrl = storeUrl,
        )
        AppLog.i(logTag) { "installed=${current ?: "unknown"} -> ${verdict::class.simpleName}" }
        setState { copy(verdict = verdict) }
    }

    private suspend fun fetchConfigOrNull() = try {
        remote.fetch()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Offline, a 5xx, or the 503 the server answers while the gate flag is off. None of those
        // is a reason to stand between the user and the app, so there is simply no prompt.
        AppLog.w(logTag, e) { "update config unavailable (${e::class.simpleName}), no prompt" }
        null
    }
}
