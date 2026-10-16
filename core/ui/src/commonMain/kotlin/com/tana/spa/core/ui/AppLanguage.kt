package com.tana.spa.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The languages the app has strings for. [tag] is the BCP 47 tag the resource lookup matches
 * (`values-en`); Vietnamese is the default `values`, which any other tag falls back to. Labels are
 * written in the language itself, so the list reads the same whichever one is showing.
 */
enum class AppLanguage(val tag: String, val label: String) {
    VIETNAMESE("vi", "Tiếng Việt"),
    ENGLISH("en", "English"),
    ;

    companion object {
        fun ofTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: VIETNAMESE
    }
}

/**
 * The language the app draws itself in. Global rather than injected because Compose resources
 * resolve the locale at the composition root: every string below it has to be read again when this
 * changes, which only the root can arrange.
 */
object SelectedLanguage {
    var value: AppLanguage by mutableStateOf(AppLanguage.VIETNAMESE)
}
