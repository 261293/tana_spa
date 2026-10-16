package com.tana.spa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

private val LocalJvmLocale = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

actual object LocalAppLocale {
    @Composable
    actual infix fun provides(tag: String): ProvidedValue<*> {
        Locale.setDefault(Locale.forLanguageTag(tag))
        return LocalJvmLocale.provides(tag)
    }
}
