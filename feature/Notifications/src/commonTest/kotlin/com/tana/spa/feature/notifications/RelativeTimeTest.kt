package com.tana.spa.feature.notifications

import com.tana.spa.feature.notifications.presentation.RelativeAge
import com.tana.spa.feature.notifications.presentation.relativeAge
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {

    private val now = 1_760_000_000_000L

    private fun ageOf(millisAgo: Long) = relativeAge(now - millisAgo, now)

    @Test
    fun anythingInsideTheLastMinuteReadsAsNow() {
        assertEquals(RelativeAge.Now, ageOf(0))
        assertEquals(RelativeAge.Now, ageOf(59_999))
    }

    @Test
    fun eachUnitTakesOverAtItsBoundary() {
        assertEquals(RelativeAge.Minutes(1), ageOf(60_000))
        assertEquals(RelativeAge.Minutes(59), ageOf(59 * 60_000L))
        assertEquals(RelativeAge.Hours(1), ageOf(60 * 60_000L))
        assertEquals(RelativeAge.Hours(23), ageOf(23 * 3_600_000L))
        assertEquals(RelativeAge.Days(1), ageOf(24 * 3_600_000L))
        assertEquals(RelativeAge.Days(6), ageOf(6 * 86_400_000L))
        assertEquals(RelativeAge.Weeks(1), ageOf(7 * 86_400_000L))
        assertEquals(RelativeAge.Weeks(8), ageOf(60 * 86_400_000L))
    }

    @Test
    fun aTimestampFromTheFutureReadsAsNowRatherThanInThreeHours() {
        // A device clock behind the server's, which is common enough to handle explicitly.
        assertEquals(RelativeAge.Now, ageOf(-3 * 3_600_000L))
    }
}
