package com.tana.spa.feature.booking

import androidx.compose.ui.unit.dp
import com.tana.spa.feature.booking.presentation.ui.calendarScale
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarScaleTest {

    @Test
    fun a_card_wide_enough_for_the_grid_is_left_alone() {
        assertEquals(1f, calendarScale(360.dp))
    }

    @Test
    fun a_wider_card_does_not_stretch_the_grid() {
        assertEquals(1f, calendarScale(600.dp))
    }

    @Test
    fun a_narrow_card_shrinks_the_grid_to_fit_it() {
        assertEquals(0.9f, calendarScale(324.dp), absoluteTolerance = 0.001f)
    }

    @Test
    fun the_grid_never_shrinks_past_half_size() {
        assertEquals(0.5f, calendarScale(100.dp))
    }
}
