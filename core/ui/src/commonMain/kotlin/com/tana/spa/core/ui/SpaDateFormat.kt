package com.tana.spa.core.ui

import com.tana.spa.shared.spa.SpaDate

/*
 * Dates and clock times in the language the app is drawn in. The names are tables rather than
 * string resources because these are plain functions: a resource lookup would make every caller
 * composable. Reading [SelectedLanguage] from composition still re-renders on a switch.
 *
 * The Vietnamese forms follow CLDR: "Th 5", "thg 10"/"tháng 10", day before month, 24-hour clock.
 */

private val weekdayShortEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val weekdayShortVi = listOf("Th 2", "Th 3", "Th 4", "Th 5", "Th 6", "Th 7", "CN")
private val weekdayLongEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val weekdayLongVi = listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")
private val monthsEn = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val vietnamese: Boolean get() = SelectedLanguage.value == AppLanguage.VIETNAMESE

fun SpaDate.weekdayShort(): String = (if (vietnamese) weekdayShortVi else weekdayShortEn)[weekdayIndex]

fun SpaDate.weekdayLong(): String = (if (vietnamese) weekdayLongVi else weekdayLongEn)[weekdayIndex]

fun SpaDate.monthShort(): String = if (vietnamese) "thg $month" else monthsEn[month - 1].take(3)

fun SpaDate.monthLong(): String = if (vietnamese) "tháng $month" else monthsEn[month - 1]

/** "Fri, Oct 17" / "Th 6, 17 thg 10" — the form the design uses wherever a date is spelled out. */
fun SpaDate.formatLong(): String = if (vietnamese) "${weekdayShort()}, $day ${monthShort()}" else "${weekdayShort()}, ${monthShort()} $day"

/** "Thursday, October 15" / "Thứ Năm, 15 tháng 10" — the staff console's date line. */
fun SpaDate.formatFull(): String = if (vietnamese) "${weekdayLong()}, $day ${monthLong()}" else "${weekdayLong()}, ${monthLong()} $day"

/** "October 2026" / "Tháng 10 năm 2026" — the calendar header. */
fun SpaDate.formatMonthYear(): String = if (vietnamese) "Tháng $month năm $year" else "${monthLong()} $year"

/** The two days a guest names rather than dates. [today] is the spa's own day, not the reader's. */
private fun SpaDate.relativeName(today: SpaDate): String? = when (epochDay) {
    today.epochDay -> if (vietnamese) "Hôm nay" else "Today"
    today.epochDay + 1 -> if (vietnamese) "Ngày mai" else "Tomorrow"
    else -> null
}

/** "Today"/"Hôm nay", "Tomorrow"/"Ngày mai", or the spelled-out date for any other day. */
fun SpaDate.formatRelative(today: SpaDate): String = relativeName(today) ?: formatLong()

/** The compact day a chip carries: the named day, or "Thu 15" / "Th 5 15". */
fun SpaDate.formatDayChip(today: SpaDate): String = relativeName(today) ?: "${weekdayShort()} $day"

/** "2:30 PM", or "14:30" in Vietnamese, from an hour-of-day such as 14.5. */
fun formatHour(hour: Double): String {
    val whole = hour.toInt()
    val minutes = ((hour - whole) * MINUTES_PER_HOUR).toInt()
    val padded = minutes.toString().padStart(2, '0')
    if (vietnamese) return "${whole.toString().padStart(2, '0')}:$padded"
    val hour12 = if (whole % NOON == 0) NOON else whole % NOON
    return "$hour12:$padded ${if (whole >= NOON) "PM" else "AM"}"
}

/** "Thu, Oct 15, 2:30 PM" — a moment the server names as a house-zone day and hour. */
fun formatDayAndTime(dayEpochDay: Int, hour: Double): String = "${SpaDate.fromEpochDay(dayEpochDay).formatLong()}, ${formatHour(hour)}"

/** The same moment as [formatDayAndTime], worded relative to [today] where that reads better. */
fun formatRelativeDayAndTime(dayEpochDay: Int, hour: Double, today: SpaDate): String =
    "${SpaDate.fromEpochDay(dayEpochDay).formatRelative(today)}, ${formatHour(hour)}"

private const val MINUTES_PER_HOUR = 60
private const val NOON = 12
