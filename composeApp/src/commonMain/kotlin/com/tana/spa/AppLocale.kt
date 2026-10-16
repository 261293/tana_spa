package com.tana.spa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key
import com.tana.spa.core.ui.SelectedLanguage

/**
 * Compose resources read the locale from the platform and there is no common API to override it
 * (`ComposeEnvironment` is internal in Compose 1.12.1), so each platform sets its own. This is the
 * workaround JetBrains documents at
 * https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html.
 */
expect object LocalAppLocale {
    @Composable
    infix fun provides(tag: String): ProvidedValue<*>
}

/**
 * Draws [content] in the chosen language. The `key` is what makes a change take effect: a string
 * is read once per composition, so the whole tree has to be composed again to re-read it.
 */
@Composable
fun AppLocale(content: @Composable () -> Unit) {
    val language = SelectedLanguage.value
    CompositionLocalProvider(LocalAppLocale provides language.tag) {
        key(language) { content() }
    }
}
