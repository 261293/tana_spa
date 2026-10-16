package com.tana.spa.feature.settings.domain

import com.tana.spa.core.ui.AppLanguage

/**
 * Where the chosen language is kept between runs. Bound in `composeApp`, like [CurrentProfile]:
 * this module does not see the cache.
 */
interface LanguageChoice {
    fun load(): AppLanguage

    fun save(language: AppLanguage)
}
