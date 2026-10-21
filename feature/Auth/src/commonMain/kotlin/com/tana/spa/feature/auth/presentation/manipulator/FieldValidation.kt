package com.tana.spa.feature.auth.presentation.manipulator

/**
 * The blur rule: check what the guest typed, but say nothing about a field they have not filled
 * in. Tabbing through a form would otherwise accuse them of leaving it empty before they have had
 * the chance to. Submit is what catches the empty ones, and it checks every field at once.
 */
internal fun <E> errorOnceTyped(value: String, validate: (String) -> E?): E? = if (value.isBlank()) null else validate(value)
