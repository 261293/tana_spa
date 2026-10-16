package com.tana.spa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual object LocalAppLocale {
    @Composable
    actual infix fun provides(tag: String): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        configuration.setLocale(locale)
        val resources = LocalContext.current.resources
        // The only way to move the resources of an already-created context; replaced by
        // per-app locales (API 33) that this app does not yet declare.
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return LocalConfiguration.provides(configuration)
    }
}
