package com.tana.spa.shared.spa

/**
 * A plain civil date. The booking screens need day arithmetic, weekday names and month lengths,
 * and the project has no date library; conversions use Howard Hinnant's days-from-civil algorithm,
 * which is exact for the proleptic Gregorian calendar.
 */
data class SpaDate(val year: Int, val month: Int, val day: Int) : Comparable<SpaDate> {

    val epochDay: Int get() = daysFromCivil(year, month, day)

    /** Monday is 0, Sunday is 6 — the design's calendar starts its weeks on Monday. */
    val weekdayIndex: Int get() = ((epochDay + 3) % 7 + 7) % 7

    fun plusDays(days: Int): SpaDate = fromEpochDay(epochDay + days)

    fun startOfMonth(): SpaDate = SpaDate(year, month, 1)

    fun plusMonths(months: Int): SpaDate {
        val zeroBased = year * 12 + (month - 1) + months
        val newYear = zeroBased.floorDiv(12)
        val newMonth = zeroBased.mod(12) + 1
        return SpaDate(newYear, newMonth, day.coerceAtMost(daysInMonth(newYear, newMonth)))
    }

    override fun compareTo(other: SpaDate): Int = epochDay.compareTo(other.epochDay)

    companion object {
        fun fromEpochDay(epochDay: Int): SpaDate = civilFromDays(epochDay)

        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    }
}

private fun daysFromCivil(year: Int, month: Int, day: Int): Int {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

private fun civilFromDays(epochDay: Int): SpaDate {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + (if (mp < 10) 3 else -9)
    return SpaDate(if (m <= 2) y + 1 else y, m, d)
}

private val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val weekdayLongNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

fun SpaDate.weekdayShort(): String = weekdayNames[weekdayIndex]

fun SpaDate.weekdayLong(): String = weekdayLongNames[weekdayIndex]

fun SpaDate.monthLong(): String = monthNames[month - 1]

fun SpaDate.monthShort(): String = monthNames[month - 1].take(3)

/** "Fri, Oct 17" — the form the design uses wherever a date is spelled out. */
fun SpaDate.formatLong(): String = "${weekdayShort()}, ${monthShort()} $day"

/** "Thursday, October 15" — the staff console's date line. */
fun SpaDate.formatFull(): String = "${weekdayLong()}, ${monthLong()} $day"

/** "October 2026" — the calendar header. */
fun SpaDate.formatMonthYear(): String = "${monthLong()} $year"
