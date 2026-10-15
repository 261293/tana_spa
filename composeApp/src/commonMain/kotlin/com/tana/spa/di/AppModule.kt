package com.tana.spa.di

import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.feature.auth.data.local.SessionStore
import com.tana.spa.feature.feed.domain.HomeUserName
import com.tana.spa.feature.settings.domain.CurrentProfile
import com.tana.spa.feature.settings.domain.SignOutAction
import com.tana.spa.feature.settings.presentation.GuestProfile
import org.koin.core.module.Module
import org.koin.dsl.module

/** Joins modules that must not know about each other; `composeApp` is the only place that sees both. */
val appModule: Module = module {
    single<AccessTokenSource> {
        val sessions = get<SessionStore>()
        // Read per call, not captured: the token changes on every sign-in and sign-out.
        AccessTokenSource { sessions.load()?.accessToken }
    }

    single<SignOutAction> {
        val sessions = get<SessionStore>()
        SignOutAction { sessions.clear() }
    }

    single<CurrentProfile> {
        val sessions = get<SessionStore>()
        CurrentProfile {
            sessions.load()?.user?.let { GuestProfile(name = it.displayName, email = it.email, phone = it.phone.orEmpty()) }
        }
    }

    single<HomeUserName> {
        val sessions = get<SessionStore>()
        HomeUserName { sessions.load()?.user?.displayName }
    }
}
