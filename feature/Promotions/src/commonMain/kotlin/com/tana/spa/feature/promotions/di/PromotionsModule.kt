package com.tana.spa.feature.promotions.di

import com.tana.spa.feature.promotions.presentation.PromotionsViewModel
import com.tana.spa.shared.spa.SpaDate
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val promotionsModule: Module = module {
    viewModel { PromotionsViewModel(SpaDate.deviceToday()) }
}
