package com.tana.spa.shared.spa

import kotlin.test.Test
import kotlin.test.assertEquals

class SpaDateTest {
    @Test
    fun epoch_day_round_trips() {
        for (date in listOf(SpaDate(1970, 1, 1), SpaDate(2026, 10, 15), SpaDate(2000, 2, 29), SpaDate(1999, 12, 31))) {
            assertEquals(date, SpaDate.fromEpochDay(date.epochDay))
        }
        assertEquals(0, SpaDate(1970, 1, 1).epochDay)
    }

    @Test
    fun weekday_counts_from_monday() {
        assertEquals(3, SpaDate(1970, 1, 1).weekdayIndex) // a Thursday
        assertEquals("Thu", SpaDate(2026, 10, 15).weekdayShort())
        assertEquals("Sun", SpaDate(2026, 10, 18).weekdayShort())
    }

    @Test
    fun adding_days_crosses_months_and_years() {
        assertEquals(SpaDate(2026, 11, 1), SpaDate(2026, 10, 15).plusDays(17))
        assertEquals(SpaDate(2027, 1, 1), SpaDate(2026, 12, 31).plusDays(1))
    }

    @Test
    fun adding_months_clamps_to_the_shorter_month() {
        assertEquals(SpaDate(2026, 2, 28), SpaDate(2026, 1, 31).plusMonths(1))
        assertEquals(SpaDate(2024, 2, 29), SpaDate(2024, 1, 31).plusMonths(1))
        assertEquals(SpaDate(2025, 12, 15), SpaDate(2026, 1, 15).plusMonths(-1))
    }

    @Test
    fun month_lengths_follow_the_leap_rule() {
        assertEquals(29, SpaDate.daysInMonth(2000, 2))
        assertEquals(28, SpaDate.daysInMonth(1900, 2))
        assertEquals(30, SpaDate.daysInMonth(2026, 11))
    }

    @Test
    fun formats_match_the_design() {
        assertEquals("Thu, Oct 15", SpaDate(2026, 10, 15).formatLong())
        assertEquals("October 2026", SpaDate(2026, 10, 15).formatMonthYear())
        assertEquals("Thursday, October 15", SpaDate(2026, 10, 15).formatFull())
    }
}
