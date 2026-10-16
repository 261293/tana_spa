package com.tana.spa.core.navigation

/** Central registry of navigation route keys used across feature modules. */
object Routes {
    const val AUTH = "auth"
    const val SIGNUP = "signup"
    const val VERIFY_PHONE = "verifyPhone"
    const val WELCOME = "welcome"
    const val CONSENT = "consent"
    const val ASSISTANT = "assistant"
    const val FEED = "feed"
    const val EXPLORE = "explore"
    const val NEW_POST = "newPost"
    const val LIKES = "likes"
    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"
    const val BOOKING = "booking"

    /** One treatment from the booking menu, named by [ARG_SERVICE]. */
    const val SERVICE_DETAIL = "serviceDetail"

    /** The signed-in guest's own visits: their status, and cancelling one. */
    const val MY_BOOKINGS = "myBookings"
    const val PROMOTIONS = "promotions"
    const val SETTINGS = "settings"

    /** Staff console, reached from the sign-in screen rather than the guest tab bar. */
    const val ADMIN_REQUESTS = "adminRequests"
    const val ADMIN_CODES = "adminCodes"

    /** The owner's menu: adding a treatment or a therapist. */
    const val ADMIN_MENU = "adminMenu"

    /** A discount code carried from the offers screen. */
    const val ARG_CODE = "code"

    /** The treatment a detail page shows. */
    const val ARG_SERVICE = "service"

    /** The visit a notification was about, so the list opens on it. */
    const val ARG_BOOKING = "booking"
}

/** Builds "booking?code=JADE20". One argument per route is all this navigator carries. */
fun String.withArg(key: String, value: String): String = "$this?$key=$value"

/** The route without its argument, which is what a [FeatureEntry] is registered under. */
fun String.routeName(): String = substringBefore('?')

/** Reads [key] back out of a route built with [withArg]; null when the route has no argument. */
fun String.routeArg(key: String): String? {
    val query = substringAfter('?', "").takeIf { it.isNotEmpty() } ?: return null
    return query.split('&')
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter('=')
        ?.takeIf { it.isNotEmpty() }
}
