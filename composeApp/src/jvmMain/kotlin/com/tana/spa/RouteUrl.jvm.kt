package com.tana.spa

/** The desktop window has no address bar: the route lives in the back stack and nowhere else. */
actual object RouteUrl {
    actual fun current(default: String): String = default
    actual fun push(route: String) = Unit
    actual fun observe(onPopped: (String) -> Unit) = Unit
}
