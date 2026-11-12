package com.tana.spa.feature.assistant.data

import com.tana.spa.core.cache.KeyValueCache
import com.tana.spa.core.logging.AppLog

class AssistantConsent(private val cache: KeyValueCache) {
    val given: Boolean get() = cache.get(KEY) == WORDING_VERSION

    fun give() {
        cache.put(KEY, WORDING_VERSION)
        AppLog.i("AssistantConsent") { "assistant consent given" }
    }

    private companion object {
        const val KEY = "assistant.consent"

        // Bump when the consent wording changes, so everyone is asked again about what they agree to.
        const val WORDING_VERSION = "1"
    }
}
