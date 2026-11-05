package com.tana.spa.shared.featureflags

import kotlinx.serialization.Serializable

/** Keyed by [FeatureFlag.key]; a key this build does not know is ignored. */
@Serializable
data class FeatureFlagsResponse(val flags: Map<String, Boolean>)
