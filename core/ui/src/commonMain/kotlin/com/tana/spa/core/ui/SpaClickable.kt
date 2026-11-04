package com.tana.spa.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role

/**
 * A clickable surface that also tells a pointer it is one. Desktop and web are the two targets with
 * a cursor, and Compose leaves the arrow in place over everything, so a card or a text link there
 * reads as inert until someone clicks it on spec. No effect on the touch targets.
 */
fun Modifier.spaClickable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier =
    spaPointer(enabled).clickable(enabled = enabled, role = role, onClick = onClick)

fun Modifier.spaPointer(enabled: Boolean = true, icon: PointerIcon = PointerIcon.Hand): Modifier =
    if (enabled) pointerHoverIcon(icon) else this
