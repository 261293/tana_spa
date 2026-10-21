package com.tana.spa.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaMotionTest {

    @Test
    fun reducedMotionLeavesNoDurationForAnythingToAnimate() {
        assertEquals(0, NoMotion.short)
        assertEquals(0, NoMotion.medium)
        assertEquals(0, NoMotion.long)
        assertEquals(0, NoMotion.exit)
        assertTrue(NoMotion.reduced)
    }

    @Test
    fun theDefaultIsOrderedShortestFirstAndAnimatesAtAll() {
        assertFalse(FullMotion.reduced)
        assertTrue(FullMotion.short > 0)
        assertTrue(FullMotion.short < FullMotion.medium)
        assertTrue(FullMotion.medium < FullMotion.long)
    }

    @Test
    fun leavingIsQuickerThanArrivingButStillVisible() {
        assertTrue(FullMotion.exit > 0)
        assertTrue(FullMotion.exit < FullMotion.medium)
    }
}
