package com.tana.spa.shared.appupdate

import kotlinx.serialization.Serializable

/**
 * [minimumVersion] is the oldest build still supported and [latestVersion] the newest released, both
 * `major.minor.patch`. [forceUpdate] escalates an available update to mandatory without moving the
 * floor, for a release that turns out to be bad.
 */
@Serializable
data class AppUpdateConfigResponse(val minimumVersion: String, val latestVersion: String, val forceUpdate: Boolean)
