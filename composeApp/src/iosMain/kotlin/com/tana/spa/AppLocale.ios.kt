package com.tana.spa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSUserDefaults

/** What iOS reads on launch to decide the bundle's language. */
private const val APPLE_LANGUAGES = "AppleLanguages"

private val LocalIosLocale = staticCompositionLocalOf { "" }

actual object LocalAppLocale {
    @Composable
    actual infix fun provides(tag: String): ProvidedValue<*> {
        NSUserDefaults.standardUserDefaults.setObject(arrayListOf(tag), APPLE_LANGUAGES)
        return LocalIosLocale.provides(tag)
    }
}
