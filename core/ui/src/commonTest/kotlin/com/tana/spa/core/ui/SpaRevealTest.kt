package com.tana.spa.core.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val VIEWPORT = 1000
private const val MARGIN = 100f

class SpaRevealTest {

    @Test
    fun an_element_below_the_fold_stays_hidden() {
        assertFalse(hasReachedViewport(topInRoot = VIEWPORT.toFloat(), viewportHeight = VIEWPORT, margin = MARGIN))
    }

    @Test
    fun an_element_reveals_a_margin_before_it_reaches_the_bottom_edge() {
        assertTrue(hasReachedViewport(topInRoot = 899f, viewportHeight = VIEWPORT, margin = MARGIN))
        assertFalse(hasReachedViewport(topInRoot = 901f, viewportHeight = VIEWPORT, margin = MARGIN))
    }

    @Test
    fun an_element_already_scrolled_past_counts_as_reached() {
        assertTrue(hasReachedViewport(topInRoot = -500f, viewportHeight = VIEWPORT, margin = MARGIN))
    }

    @Test
    fun an_unmeasured_window_reveals_rather_than_leaving_content_invisible() {
        assertTrue(hasReachedViewport(topInRoot = 5_000f, viewportHeight = UNMEASURED_VIEWPORT, margin = MARGIN))
    }

    @Test
    fun a_wider_window_travels_further() {
        assertTrue(revealTravelFor(SpaWindowSize.COMPACT) < revealTravelFor(SpaWindowSize.MEDIUM))
        assertTrue(revealTravelFor(SpaWindowSize.MEDIUM) < revealTravelFor(SpaWindowSize.EXPANDED))
        assertEquals(revealTravelFor(SpaWindowSize.EXPANDED), revealTravelFor(SpaWindowSize.LARGE))
    }

    @Test
    fun every_window_size_travels_a_visible_distance() {
        for (size in SpaWindowSize.entries) assertTrue(revealTravelFor(size) > 0.dp, size.name)
    }
}
