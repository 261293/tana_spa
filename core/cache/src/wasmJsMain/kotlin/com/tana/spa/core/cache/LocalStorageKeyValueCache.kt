package com.tana.spa.core.cache

import kotlinx.browser.localStorage

/**
 * The browser's own store, so a reload comes back to the screen the guest was on instead of to
 * sign-in. Keys are prefixed because localStorage belongs to the whole origin, not to this app.
 *
 * Every call is guarded: localStorage *throws* rather than answering when the browser refuses
 * site data — a private window, or third-party storage blocked while the app runs in a frame —
 * and a cache that cannot store anything is still a working app, only a forgetful one.
 */
class LocalStorageKeyValueCache : KeyValueCache {

    override fun put(key: String, value: String) {
        runCatching { localStorage.setItem(PREFIX + key, value) }
    }

    override fun get(key: String): String? = runCatching { localStorage.getItem(PREFIX + key) }.getOrNull()

    override fun clear() {
        runCatching {
            // Collected before anything is removed: removing shifts every later index down one.
            val ours = (0 until localStorage.length).mapNotNull { localStorage.key(it) }.filter { it.startsWith(PREFIX) }
            ours.forEach { localStorage.removeItem(it) }
        }
    }

    private companion object {
        const val PREFIX = "tana."
    }
}
