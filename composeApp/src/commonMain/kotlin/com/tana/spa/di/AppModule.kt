package com.tana.spa.di

import com.tana.spa.core.featureflags.AccessTokenSource
import com.tana.spa.feature.auth.data.local.SessionStore
import com.tana.spa.feature.feed.domain.HomeUserName
import org.koin.core.module.Module
import org.koin.dsl.module

/** Joins modules that must not know about each other; `composeApp` is the only place that sees both. */
val appModule: Module = module {
    single<AccessTokenSource> {
        val sessions = get<SessionStore>()
        // Read per call, not captured: the token changes on every sign-in and sign-out.
        AccessTokenSource { sessions.load()?.accessToken }
    }

    single<HomeUserName> {
        val sessions = get<SessionStore>()
        HomeUserName { sessions.load()?.user?.displayName }
    }
}
