package com.tana.spa.feature.feed.di

import com.tana.spa.feature.feed.data.cache.FeedCache
import com.tana.spa.feature.feed.data.cache.source.FeedCacheImpl
import com.tana.spa.feature.feed.data.remote.FeedRemoteApi
import com.tana.spa.feature.feed.data.remote.source.FeedRemoteApiImpl
import com.tana.spa.feature.feed.data.repository.FeedRepositoryImpl
import com.tana.spa.feature.feed.domain.repository.FeedRepository
import com.tana.spa.feature.feed.presentation.manipulator.FeedViewmodel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val feedModule: Module = module {
    single<FeedRemoteApi> { FeedRemoteApiImpl(get()) }
    single<FeedCache> { FeedCacheImpl(get()) }
    single<FeedRepository> { FeedRepositoryImpl(get(), get()) }
    viewModelOf(::FeedViewmodel)
}
