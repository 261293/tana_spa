package com.tana.spa.feature.settings.domain

/**
 * Ends the session. The session itself lives in `feature:Auth`, which this module must not depend
 * on, so `composeApp` binds this the way it binds `AccessTokenSource`.
 */
fun interface SignOutAction {
    fun signOut()
}
