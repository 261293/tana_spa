package com.tana.spa.core.network

import org.koin.core.module.Module
import org.koin.dsl.module

val networkModule: Module = module {
    // The refresher is resolved per 401, not now: it needs the session, whose own calls need this
    // client, and asking for it here would close that loop while the client is still being built.
    single { NetworkClient(platformHttpClient().withDefaults { get<SessionRefresher>() }) }
}
