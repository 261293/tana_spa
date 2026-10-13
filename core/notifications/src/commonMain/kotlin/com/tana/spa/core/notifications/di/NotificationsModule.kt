package com.tana.spa.core.notifications.di

import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.notifications.NotificationBadge
import com.tana.spa.core.notifications.PushRegistrar
import com.tana.spa.core.notifications.data.NotificationRemoteApi
import com.tana.spa.core.notifications.data.NotificationRemoteApiImpl
import com.tana.spa.shared.Constants
import com.tana.spa.shared.Platform
import org.koin.core.module.Module
import org.koin.dsl.module

val notificationsCoreModule: Module = module {
    includes(pushPlatformModule)

    single<NotificationRemoteApi> {
        NotificationRemoteApiImpl(
            networkClient = get(),
            baseUrl = Constants.apiBaseUrl(get<Platform>().type),
            // Without a session bound (a test host, or before composeApp wires one) every call is
            // anonymous and the server answers 401, which the registrar reads as "not signed in".
            tokens = getOrNull<AccessTokenSource>() ?: AccessTokenSource.none,
        )
    }

    single {
        NotificationBadge(
            remote = get(),
            flags = get(),
            sessions = getOrNull<AccessTokenSource>() ?: AccessTokenSource.none,
        )
    }

    single {
        PushRegistrar(
            remote = get(),
            cache = get(),
            permissions = get(),
            tokens = get(),
            flags = get(),
            sessions = getOrNull<AccessTokenSource>() ?: AccessTokenSource.none,
        )
    }
}

/** Each target supplies its own permission prompt and its own token source. */
expect val pushPlatformModule: Module
