package com.tana.spa.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Remembers a simple in-memory back-stack navigator for the given [start] route. */
@Composable
fun rememberNavigator(start: String): MutableNavigator = remember { MutableNavigator(start) }

/** A minimal back-stack [Navigator]; a full build would delegate to navigation-compose. */
class MutableNavigator(start: String) : Navigator {
    private val stack = mutableListOf(start)

    var current: String by mutableStateOf(start)
        private set

    override fun navigate(route: String) {
        stack.add(route)
        current = route
    }

    override fun replaceAll(route: String) {
        stack.clear()
        stack.add(route)
        current = route
    }

    override fun back() {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            current = stack.last()
        }
    }
}
