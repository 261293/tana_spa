package com.tana.spa.core.featureflags

import com.tana.spa.core.cache.InMemoryKeyValueCache
import com.tana.spa.core.cache.KeyValueCache
import com.tana.spa.shared.featureflags.FeatureFlag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureFlagStoreTest {

    private class FakeRemote(var flags: Map<String, Boolean> = emptyMap()) : FeatureFlagRemoteApi {
        var error: Exception? = null
        var calls = 0
        var lastToken: String? = null

        /** When set, `fetch` suspends until completed: lets a test hold a request "in flight". */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun fetch(accessToken: String?): Map<String, Boolean> {
            calls++
            lastToken = accessToken
            gate?.await()
            error?.let { throw it }
            return flags
        }
    }

    private val otp = FeatureFlag.PHONE_OTP_VERIFICATION
    private val remote = FakeRemote()
    private val cache = InMemoryKeyValueCache()
    private var token: String? = null
    private var now = 1_000L

    private fun store(cache: KeyValueCache = this.cache, staleAfterMillis: Long = 5 * 60 * 1000L) = FeatureFlagStore(
        remote = remote,
        cache = cache,
        tokens = AccessTokenSource { token },
        nowMillis = { now },
        staleAfterMillis = staleAfterMillis,
    )

    @Test
    fun beforeAnythingIsFetchedTheCompiledDefaultsApply() {
        assertEquals(otp.defaultEnabled, store().isEnabled(otp))
    }

    @Test
    fun theBackendDecidesOnceItHasAnswered() = runTest {
        remote.flags = mapOf(otp.key to true)
        val store = store()

        store.refresh()

        assertTrue(store.isEnabled(otp))
        assertEquals(mapOf(otp to true), store.flags.value)
    }

    @Test
    fun theSignedInUsersTokenIsSentSoPerUserOverridesApply() = runTest {
        token = "user-token"
        remote.flags = mapOf(otp.key to true)

        store().refresh()

        assertEquals("user-token", remote.lastToken)
    }

    @Test
    fun aColdStartOfflineBehavesLikeTheLastRunDid() = runTest {
        remote.flags = mapOf(otp.key to true)
        store().refresh()

        // New store, same cache: the process restarted.
        val restarted = store()
        remote.error = IllegalStateException("offline")
        assertTrue(restarted.isEnabled(otp), "the cached answer applies before any network call")

        restarted.refresh()
        assertTrue(restarted.isEnabled(otp), "a failed refresh must not revert to the default")
    }

    @Test
    fun aFailedFirstRefreshLeavesTheDefaultsInPlace() = runTest {
        remote.error = IllegalStateException("boom")
        val store = store()

        store.refresh()

        assertEquals(otp.defaultEnabled, store.isEnabled(otp))
    }

    @Test
    fun aFlagThisBuildDoesNotKnowIsIgnored() = runTest {
        remote.flags = mapOf("flag_from_a_newer_server" to true, otp.key to true)
        val store = store()

        store.refresh()

        assertEquals(mapOf(otp to true), store.flags.value)
    }

    @Test
    fun unreadableCachedDataFallsBackToTheDefaultsInsteadOfThrowing() {
        val corrupted = InMemoryKeyValueCache().apply { put("feature_flags.v1", "{not json") }

        val store = store(cache = corrupted)

        assertEquals(otp.defaultEnabled, store.isEnabled(otp))
    }

    @Test
    fun aCacheThatThrowsIsNotFatal() {
        val broken = object : KeyValueCache {
            override fun put(key: String, value: String) = throw IllegalStateException("no storage")
            override fun get(key: String): String? = throw IllegalStateException("no storage")
            override fun clear() = Unit
        }

        assertEquals(otp.defaultEnabled, store(cache = broken).isEnabled(otp))
    }

    @Test
    fun refreshIfStaleWaitsForTheTtlButNotForAChangeOfUser() = runTest {
        remote.flags = mapOf(otp.key to true)
        val store = store(staleAfterMillis = 1_000)

        store.refreshIfStale()
        assertEquals(1, remote.calls, "nothing fetched yet, so it fetches")

        now += 500
        store.refreshIfStale()
        assertEquals(1, remote.calls, "still fresh")

        token = "user-token"
        store.refreshIfStale()
        assertEquals(2, remote.calls)

        now += 2_000
        store.refreshIfStale()
        assertEquals(3, remote.calls, "older than the TTL")
    }

    @Test
    fun signingOutIsAlsoAChangeOfUser() = runTest {
        token = "user-token"
        val store = store()
        store.refreshIfStale()

        token = null
        store.refreshIfStale()

        assertEquals(2, remote.calls)
        assertNull(remote.lastToken)
    }

    @Test
    fun twoScreensOpeningAtOnceCauseOneRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        remote.gate = gate
        remote.flags = mapOf(otp.key to true)
        val store = store()

        val first = launch { store.refresh() }
        testScheduler.runCurrent() // let it reach the request and suspend there

        store.refresh() // a second screen asks while the first request is still open
        assertEquals(1, remote.calls)

        gate.complete(Unit)
        first.join()
        assertTrue(store.isEnabled(otp))
    }

    @Test
    fun aBackendThatDropsAFlagHandsItBackToTheCompiledDefault() = runTest {
        remote.flags = mapOf(otp.key to !otp.defaultEnabled)
        val store = store()
        store.refresh()
        assertEquals(!otp.defaultEnabled, store.isEnabled(otp))

        remote.flags = emptyMap()
        store.refresh()

        assertEquals(otp.defaultEnabled, store.isEnabled(otp))
        assertFalse(store.flags.value.containsKey(otp))
    }
}
