package com.tana.spa.feature.promotions.di

import com.tana.spa.feature.promotions.presentation.PromotionsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val promotionsModule: Module = module {
    viewModelOf(::PromotionsViewModel)
}
