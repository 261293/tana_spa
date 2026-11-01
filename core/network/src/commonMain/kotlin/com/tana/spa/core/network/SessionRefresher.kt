package com.tana.spa.core.network

/**
 * Trades an access token the server has rejected for a fresh one. `composeApp` binds it, because
 * the session lives in `feature:Auth` and `core` must not depend on a feature.
 *
 * An implementation has to be single-flight. Refresh tokens rotate: two calls refreshing at once
 * would spend the same one twice, and the second attempt looks to the server exactly like a
 * replayed token, which revokes every token of that sign-in.
 */
fun interface SessionRefresher {
    /**
     * @param staleToken the bearer token the rejected call carried, so an implementation can tell
     *   "mine is out of date" from "somebody already replaced it while I waited".
     * @return the token to retry with, or null when the guest has to sign in again.
     */
    suspend fun refresh(staleToken: String?): String?

    companion object {
        /** For a test host, or before `composeApp` has bound a session. */
        val none: SessionRefresher = SessionRefresher { null }
    }
}
