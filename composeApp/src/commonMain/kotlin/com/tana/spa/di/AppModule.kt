package com.tana.spa.di

import com.tana.spa.core.cache.KeyValueCache
import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.network.CallerRole
import com.tana.spa.core.network.SessionRefresher
import com.tana.spa.core.ui.AppLanguage
import com.tana.spa.feature.auth.data.SessionRefreshCoordinator
import com.tana.spa.feature.auth.data.local.SessionStore
import com.tana.spa.feature.auth.domain.repository.AuthRepository
import com.tana.spa.feature.feed.domain.HomeUserName
import com.tana.spa.feature.settings.domain.CurrentProfile
import com.tana.spa.feature.settings.domain.LanguageChoice
import com.tana.spa.feature.settings.domain.SignOutAction
import com.tana.spa.feature.settings.presentation.GuestProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.dsl.module

/** Joins modules that must not know about each other; `composeApp` is the only place that sees both. */
val appModule: Module = module {
    single<AccessTokenSource> {
        val sessions = get<SessionStore>()
        // Read per call, not captured: the token changes on every sign-in and sign-out.
        AccessTokenSource { sessions.load()?.accessToken }
    }

    single<CallerRole> {
        val sessions = get<SessionStore>()
        CallerRole { sessions.load()?.user?.role?.wire.orEmpty() }
    }

    single<SessionRefresher> { get<SessionRefreshCoordinator>() }

    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single<SignOutAction> {
        val sessions = get<SessionStore>()
        val repository = get<AuthRepository>()
        val scope = get<CoroutineScope>()
        // Cleared here and now, because the screen navigates to sign-in on the next line and the
        // form reads the session as it opens. Telling the server is best effort and can lag.
        SignOutAction {
            val token = sessions.load()?.accessToken.orEmpty()
            sessions.clear()
            scope.launch { repository.revokeSession(token) }
        }
    }

    single<CurrentProfile> {
        val sessions = get<SessionStore>()
        CurrentProfile {
            sessions.load()?.user?.let { GuestProfile(name = it.displayName, email = it.email, phone = it.phone.orEmpty()) }
        }
    }

    single<LanguageChoice> {
        val cache = get<KeyValueCache>()
        object : LanguageChoice {
            override fun load() = AppLanguage.ofTag(cache.get(LANGUAGE_KEY))

            override fun save(language: AppLanguage) = cache.put(LANGUAGE_KEY, language.tag)
        }
    }

    single<HomeUserName> {
        val sessions = get<SessionStore>()
        HomeUserName { sessions.load()?.user?.displayName }
    }
}

// The web target's cache is in memory, so a browser reload falls back to the default language.
private const val LANGUAGE_KEY = "app.language"
