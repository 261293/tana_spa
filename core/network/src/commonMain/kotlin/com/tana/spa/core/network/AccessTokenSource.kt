package com.tana.spa.core.network

/**
 * The bearer token for calls that need a signed-in account.
 *
 * The session lives in `feature:Auth`, which `core` must not depend on, so `composeApp` binds this
 * and every core/feature module that calls an authenticated endpoint injects it. Read it per call
 * rather than capturing the value: it changes on every sign-in and sign-out.
 */
fun interface AccessTokenSource {
    fun accessToken(): String?

    companion object {
        /** Anonymous: for a test host, or before `composeApp` has bound a session. */
        val none: AccessTokenSource = AccessTokenSource { null }
    }
}
