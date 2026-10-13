package com.tana.spa.core.featureflags.di

import com.tana.spa.core.featureflags.FeatureFlagRemoteApi
import com.tana.spa.core.featureflags.FeatureFlagRemoteApiImpl
import com.tana.spa.core.featureflags.FeatureFlagStore
import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.shared.Constants
import com.tana.spa.shared.Platform
import com.tana.spa.shared.featureflags.FeatureFlags
import org.koin.core.module.Module
import org.koin.dsl.module

val featureFlagsModule: Module = module {
    single<FeatureFlagRemoteApi> { FeatureFlagRemoteApiImpl(get(), Constants.apiBaseUrl(get<Platform>().type)) }
    single {
        // Without a session (a test host, or before composeApp binds one) the flags that apply to
        // everybody are the right answer, not a startup crash.
        FeatureFlagStore(get(), get(), getOrNull<AccessTokenSource>() ?: AccessTokenSource.none)
    }
    single<FeatureFlags> { get<FeatureFlagStore>() }
}
