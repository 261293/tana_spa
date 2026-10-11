package com.tana.spa.feature.appupdate.domain

import com.tana.spa.shared.appupdate.AppUpdateConfigResponse
import com.tana.spa.shared.appupdate.AppVersion

sealed interface UpdateVerdict {
    data object None : UpdateVerdict
    data class Optional(val latestVersion: AppVersion) : UpdateVerdict
    data class Forced(val latestVersion: AppVersion) : UpdateVerdict
}

/**
 * `minimumVersion` is the floor, `latestVersion` decides whether there is anything to offer, and
 * `forceUpdate` escalates an optional prompt to mandatory. Equality is not below the floor, so a
 * build exactly at `minimumVersion` is not forced.
 *
 * Every unknown — the flag off, no config, an unreadable version, no store link — resolves to
 * [UpdateVerdict.None], so the gate can never be the reason the app is unusable.
 */
fun updateVerdict(installed: String?, config: AppUpdateConfigResponse?, gateEnabled: Boolean, storeUrl: String): UpdateVerdict {
    if (!gateEnabled || config == null || storeUrl.isBlank()) return UpdateVerdict.None
    val current = installed?.let(AppVersion::parseOrNull) ?: return UpdateVerdict.None
    val minimum = AppVersion.parseOrNull(config.minimumVersion) ?: return UpdateVerdict.None
    val latest = AppVersion.parseOrNull(config.latestVersion) ?: return UpdateVerdict.None

    if (current < minimum) return UpdateVerdict.Forced(latest)
    if (current >= latest) return UpdateVerdict.None
    return if (config.forceUpdate) UpdateVerdict.Forced(latest) else UpdateVerdict.Optional(latest)
}
