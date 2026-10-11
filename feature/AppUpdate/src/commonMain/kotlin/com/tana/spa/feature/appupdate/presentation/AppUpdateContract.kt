package com.tana.spa.feature.appupdate.presentation

import com.tana.spa.feature.appupdate.domain.UpdateVerdict

data class AppUpdateState(val verdict: UpdateVerdict = UpdateVerdict.None, val dismissed: Boolean = false) {

    /** A forced prompt ignores [dismissed]: there is nothing for the user to decide. */
    val visible: Boolean = when (verdict) {
        UpdateVerdict.None -> false
        is UpdateVerdict.Forced -> true
        is UpdateVerdict.Optional -> !dismissed
    }
}

sealed interface AppUpdateEvent {
    data object UpdateClicked : AppUpdateEvent
    data object Dismissed : AppUpdateEvent
}

sealed interface AppUpdateEffect {
    data class OpenStore(val url: String) : AppUpdateEffect
}
