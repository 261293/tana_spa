package com.tana.spa

import kotlinx.browser.window

/**
 * The route rides in the fragment — `#settings`, `#booking?code=JADE20`. Both the dev server and
 * the published distribution serve one index.html from the root, so a path would 404 on reload
 * while a fragment always comes back to the app.
 */
actual object RouteUrl {
    actual fun current(default: String): String = routeInUrl() ?: default

    actual fun push(route: String) {
        // Also the guard that keeps Back working: the route a popstate just restored is already
        // in the bar, and pushing it again would bury the entry the browser moved to.
        if (routeInUrl() == route) return
        window.history.pushState(null, "", "#$route")
    }

    actual fun observe(onPopped: (String) -> Unit) {
        window.addEventListener("popstate") { routeInUrl()?.let(onPopped) }
    }

    private fun routeInUrl(): String? = window.location.hash.removePrefix("#").takeIf { it.isNotEmpty() }
}
