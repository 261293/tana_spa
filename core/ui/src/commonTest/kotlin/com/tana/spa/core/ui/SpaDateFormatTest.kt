package com.tana.spa.core.ui

import com.tana.spa.shared.spa.SpaDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SpaDateFormatTest {

    private val thursday = SpaDate(2026, 10, 15)

    @AfterTest
    fun restoreLanguage() {
        SelectedLanguage.value = AppLanguage.VIETNAMESE
    }

    @Test
    fun english_keeps_the_wording_the_design_was_drawn_with() {
        SelectedLanguage.value = AppLanguage.ENGLISH
        assertEquals("Thu, Oct 15", thursday.formatLong())
        assertEquals("Thursday, October 15", thursday.formatFull())
        assertEquals("October 2026", thursday.formatMonthYear())
        assertEquals("Sun", SpaDate(2026, 10, 18).weekdayShort())
    }

    @Test
    fun vietnamese_puts_the_day_before_the_month() {
        SelectedLanguage.value = AppLanguage.VIETNAMESE
        assertEquals("Th 5, 15 thg 10", thursday.formatLong())
        assertEquals("Thứ Năm, 15 tháng 10", thursday.formatFull())
        assertEquals("Tháng 10 năm 2026", thursday.formatMonthYear())
        assertEquals("CN", SpaDate(2026, 10, 18).weekdayShort())
    }

    @Test
    fun english_clock_is_twelve_hour_and_vietnamese_is_twenty_four() {
        SelectedLanguage.value = AppLanguage.ENGLISH
        assertEquals("2:30 PM", formatHour(14.5))
        assertEquals("12:00 AM", formatHour(0.0))
        assertEquals("12:00 PM", formatHour(12.0))
        assertEquals("9:00 AM", formatHour(9.0))

        SelectedLanguage.value = AppLanguage.VIETNAMESE
        assertEquals("14:30", formatHour(14.5))
        assertEquals("00:00", formatHour(0.0))
        assertEquals("09:00", formatHour(9.0))
    }

    @Test
    fun today_and_tomorrow_are_named_rather_than_dated() {
        SelectedLanguage.value = AppLanguage.ENGLISH
        assertEquals("Today", thursday.formatRelative(thursday))
        assertEquals("Tomorrow", thursday.plusDays(1).formatRelative(thursday))
        assertEquals("Sat, Oct 17", thursday.plusDays(2).formatRelative(thursday))
        assertEquals("Wed, Oct 14", thursday.plusDays(-1).formatRelative(thursday), "yesterday is a date, not a name")

        SelectedLanguage.value = AppLanguage.VIETNAMESE
        assertEquals("Hôm nay", thursday.formatRelative(thursday))
        assertEquals("Ngày mai", thursday.plusDays(1).formatRelative(thursday))
        assertEquals("Th 7, 17 thg 10", thursday.plusDays(2).formatRelative(thursday))
    }

    @Test
    fun a_day_chip_names_today_and_stays_compact_otherwise() {
        SelectedLanguage.value = AppLanguage.VIETNAMESE
        assertEquals("Hôm nay", thursday.formatDayChip(thursday))
        assertEquals("Ngày mai", thursday.plusDays(1).formatDayChip(thursday))
        assertEquals("Th 7 17", thursday.plusDays(2).formatDayChip(thursday))
    }

    @Test
    fun a_named_day_carries_its_time_with_it() {
        SelectedLanguage.value = AppLanguage.VIETNAMESE
        assertEquals("Hôm nay, 14:30", formatRelativeDayAndTime(thursday.epochDay, 14.5, thursday))
        assertEquals("Th 7, 17 thg 10, 14:30", formatRelativeDayAndTime(thursday.epochDay + 2, 14.5, thursday))
    }

    @Test
    fun a_moment_the_server_named_reads_as_a_date_and_a_time() {
        SelectedLanguage.value = AppLanguage.ENGLISH
        assertEquals("Thu, Oct 15, 2:30 PM", formatDayAndTime(thursday.epochDay, 14.5))
    }
}
