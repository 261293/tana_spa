package com.tana.spa

/**
 * The address bar, on the one target that has one. A browser reload starts the app from scratch,
 * so unless the URL names the screen the guest was on they land back at sign-in; and a URL that
 * never changes makes the browser's own Back and Forward meaningless.
 *
 * Every other target answers with the route it was given: the back stack is the only history.
 */
expect object RouteUrl {
    /** The route the URL names, or [default] when it names none. */
    fun current(default: String): String

    /** Shows [route] in the address bar as a new history entry, unless it is already the one shown. */
    fun push(route: String)

    /** Calls [onPopped] when the browser moves inside its own history: Back, Forward, a typed URL. */
    fun observe(onPopped: (String) -> Unit)
}
