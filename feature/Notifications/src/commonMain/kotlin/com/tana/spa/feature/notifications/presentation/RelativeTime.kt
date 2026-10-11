package com.tana.spa.feature.notifications.presentation

/**
 * How old a notification is, in the coarsest unit that still reads naturally. Deliberately not a
 * date: beyond a few weeks the exact day stops mattering in a notification list, and formatting
 * one would need a date library on every target.
 */
sealed interface RelativeAge {
    data object Now : RelativeAge
    data class Minutes(val value: Int) : RelativeAge
    data class Hours(val value: Int) : RelativeAge
    data class Days(val value: Int) : RelativeAge
    data class Weeks(val value: Int) : RelativeAge
}

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
private const val DAY_MILLIS = 24 * HOUR_MILLIS
private const val WEEK_MILLIS = 7 * DAY_MILLIS

/**
 * A clock that has drifted behind the server, or a timestamp from a page cached before a
 * device-time change, would otherwise render as "in 3 hours"; those count as [RelativeAge.Now].
 */
fun relativeAge(createdAtEpochMillis: Long, nowEpochMillis: Long): RelativeAge {
    val elapsed = nowEpochMillis - createdAtEpochMillis
    return when {
        elapsed < MINUTE_MILLIS -> RelativeAge.Now
        elapsed < HOUR_MILLIS -> RelativeAge.Minutes((elapsed / MINUTE_MILLIS).toInt())
        elapsed < DAY_MILLIS -> RelativeAge.Hours((elapsed / HOUR_MILLIS).toInt())
        elapsed < WEEK_MILLIS -> RelativeAge.Days((elapsed / DAY_MILLIS).toInt())
        else -> RelativeAge.Weeks((elapsed / WEEK_MILLIS).toInt())
    }
}
