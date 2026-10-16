package com.tana.spa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalWebLocale = staticCompositionLocalOf { "" }

actual object LocalAppLocale {
    @Composable
    actual infix fun provides(tag: String): ProvidedValue<*> {
        setCustomLocale(tag)
        return LocalWebLocale.provides(tag)
    }
}

// `navigator.languages` is read-only, so index.html installs a getter that answers this instead.
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun setCustomLocale(tag: String) {
    js(
        """
        if (window.__tanaLocale !== tag) {
            window.__tanaLocale = tag;
            window.dispatchEvent(new Event("languagechange"));
        }
        """,
    )
}
