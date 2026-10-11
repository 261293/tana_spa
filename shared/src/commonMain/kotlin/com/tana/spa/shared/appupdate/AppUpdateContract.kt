package com.tana.spa.shared.appupdate

import kotlinx.serialization.Serializable

object AppUpdateApi {
    const val BASE = "/api/v1/app-update"

    const val CONFIG = BASE
}

/**
 * [minimumVersion] is the oldest build still supported and [latestVersion] the newest released, both
 * `major.minor.patch`. [forceUpdate] escalates an available update to mandatory without moving the
 * floor, for a release that turns out to be bad.
 */
@Serializable
data class AppUpdateConfigResponse(val minimumVersion: String, val latestVersion: String, val forceUpdate: Boolean)

@Serializable
data class AppUpdateConfigWriteRequest(val minimumVersion: String, val latestVersion: String, val forceUpdate: Boolean)
