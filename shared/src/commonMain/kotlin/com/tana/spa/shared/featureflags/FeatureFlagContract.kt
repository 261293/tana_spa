package com.tana.spa.shared.featureflags

import kotlinx.serialization.Serializable

object FeatureFlagApi {
    const val BASE = "/api/v1/feature-flags"

    const val RESOLVED = BASE

    const val ADMIN = "$BASE/admin"

    fun global(key: String): String = "$BASE/$key"

    fun forUser(key: String, userId: String): String = "$BASE/$key/users/$userId"
}

/** Keyed by [FeatureFlag.key]; a key this build does not know is ignored. */
@Serializable
data class FeatureFlagsResponse(val flags: Map<String, Boolean>)

@Serializable
data class FeatureFlagWriteRequest(val enabled: Boolean, val note: String? = null)

@Serializable
data class FeatureFlagOverrideDto(val userId: String, val enabled: Boolean, val note: String? = null)

@Serializable
data class FeatureFlagAdminDto(
    val key: String,
    val description: String,
    /** What this caller gets with no override: [globalEnabled], or [deployDefault] when unset. */
    val enabled: Boolean,
    /** Null when nothing is stored and the deployment decides. */
    val globalEnabled: Boolean? = null,
    val deployDefault: Boolean,
    val overrides: List<FeatureFlagOverrideDto> = emptyList(),
)

@Serializable
data class FeatureFlagAdminResponse(val flags: List<FeatureFlagAdminDto>)
