package com.tana.spa.feature.auth.presentation.manipulator

internal fun <E> errorOnceTyped(value: String, validate: (String) -> E?): E? = if (value.isBlank()) null else validate(value)
