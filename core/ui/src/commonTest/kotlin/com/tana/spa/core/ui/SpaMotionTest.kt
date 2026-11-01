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

    @Test
    fun aRevealOutlastsTheOtherTransitionsWithoutDragging() {
        assertTrue(FullMotion.reveal > FullMotion.long)
        assertTrue(FullMotion.reveal <= LONGEST_ACCEPTABLE_REVEAL_MS)
    }

    @Test
    fun theStaggerBuildsThenStopsSoALongListsTailIsNotLeftWaiting() {
        assertEquals(0, FullMotion.staggerFor(0))
        assertEquals(FullMotion.stagger, FullMotion.staggerFor(1))
        assertEquals(FullMotion.staggerFor(3), FullMotion.staggerFor(50))
        assertTrue(FullMotion.staggerFor(50) < FullMotion.reveal)
    }

    @Test
    fun reducedMotionStaggersNothingEither() {
        assertEquals(0, NoMotion.reveal)
        assertEquals(0, NoMotion.staggerFor(3))
    }

    private companion object {
        const val LONGEST_ACCEPTABLE_REVEAL_MS = 600
    }
}
