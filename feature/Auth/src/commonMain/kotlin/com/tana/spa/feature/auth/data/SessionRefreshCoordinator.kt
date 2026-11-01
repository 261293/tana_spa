package com.tana.spa.feature.auth.data

import com.tana.spa.core.network.SessionRefresher
import com.tana.spa.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Makes refreshing single-flight.
 *
 * Refresh tokens rotate, so spending one twice is indistinguishable from a replay and costs the
 * guest every device signed in from that account. Several calls can be refused at once — a screen
 * that loads three things on open is enough — so the lock is what keeps those three 401s from
 * becoming three rotations of the same token.
 */
class SessionRefreshCoordinator(private val repository: AuthRepository) : SessionRefresher {
    private val mutex = Mutex()

    override suspend fun refresh(staleToken: String?): String? = mutex.withLock {
        val current = repository.currentSession()
        if (current != null && current.accessToken != staleToken) return@withLock current.accessToken
        repository.refreshSession()?.accessToken
    }
}
