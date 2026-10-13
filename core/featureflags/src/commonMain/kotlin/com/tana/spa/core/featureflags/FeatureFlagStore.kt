package com.tana.spa.core.featureflags

import com.tana.spa.core.cache.KeyValueCache
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlags
import com.tana.spa.shared.featureflags.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * Reads are synchronous because the callers — view models deciding whether a step exists — cannot
 * wait for a network call. [isEnabled] therefore answers from the last snapshot the backend gave,
 * or the one cached from an earlier run, or the state compiled into [FeatureFlag].
 */
class FeatureFlagStore(
    private val remote: FeatureFlagRemoteApi,
    private val cache: KeyValueCache,
    private val tokens: AccessTokenSource,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
) : FeatureFlags {

    private val state = MutableStateFlow(cachedFlags())

    val flags: StateFlow<Map<FeatureFlag, Boolean>> = state.asStateFlow()

    private var lastFetchedAtMillis: Long? = null
    private var lastFetchedToken: String? = null
    private var refreshInFlight = false

    override fun isEnabled(flag: FeatureFlag): Boolean = state.value[flag] ?: flag.defaultEnabled

    suspend fun refreshIfStale() {
        if (!isFresh()) refresh()
    }

    suspend fun refresh() {
        if (refreshInFlight) return
        refreshInFlight = true
        val token = tokens.accessToken()
        try {
            adopt(remote.fetch(token), token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline, a 5xx or an unreadable body: a flag that is off must stay off rather than
            // come back because the network dropped.
            AppLog.w(TAG, e) { "flag refresh failed (${e::class.simpleName}), keeping: ${describe()}" }
        } finally {
            refreshInFlight = false
        }
    }

    private fun isFresh(): Boolean {
        val fetchedAt = lastFetchedAtMillis ?: return false
        return tokens.accessToken() == lastFetchedToken && nowMillis() - fetchedAt < staleAfterMillis
    }

    private fun adopt(fetched: Map<String, Boolean>, token: String?) {
        state.value = toCatalogFlags(fetched)
        lastFetchedAtMillis = nowMillis()
        lastFetchedToken = token
        writeCache(fetched)
        AppLog.i(TAG) { "flags refreshed (${if (token == null) "anonymous" else "signed in"}): ${describe()}" }
    }

    private fun cachedFlags(): Map<FeatureFlag, Boolean> {
        val stored = readCache() ?: return emptyMap()
        return try {
            toCatalogFlags(json.decodeFromString<Map<String, Boolean>>(stored))
        } catch (e: Exception) {
            AppLog.w(TAG, e) { "cached flags are unreadable (${e::class.simpleName}), using the defaults" }
            emptyMap()
        }
    }

    private fun toCatalogFlags(raw: Map<String, Boolean>): Map<FeatureFlag, Boolean> =
        raw.mapNotNull { (key, enabled) -> FeatureFlag.fromKey(key)?.let { it to enabled } }.toMap()

    private fun readCache(): String? = try {
        cache.get(CACHE_KEY)
    } catch (e: Exception) {
        AppLog.w(TAG, e) { "could not read cached flags (${e::class.simpleName})" }
        null
    }

    private fun writeCache(fetched: Map<String, Boolean>) {
        try {
            // Raw keys, not this build's enum, so a later build can still read what we stored.
            cache.put(CACHE_KEY, json.encodeToString(fetched))
        } catch (e: Exception) {
            AppLog.w(TAG, e) { "could not cache flags (${e::class.simpleName})" }
        }
    }

    private companion object {
        const val TAG = "FeatureFlags"
        const val CACHE_KEY = "feature_flags.v1"
        const val DEFAULT_STALE_AFTER_MILLIS = 5 * 60 * 1000L

        val json = Json { ignoreUnknownKeys = true }
    }
}
