package com.tana.spa.feature.settings.domain

import com.tana.spa.feature.settings.presentation.GuestProfile

/**
 * The signed-in account. The session lives in `feature:Auth`, which this module must not depend
 * on, so `composeApp` binds this the way it binds [SignOutAction].
 */
fun interface CurrentProfile {
    /** Null when nothing is signed in, which the screen draws as an empty profile. */
    fun load(): GuestProfile?
}
