package com.tana.spa.feature.booking

import com.tana.spa.feature.booking.domain.GuestBooking
import com.tana.spa.feature.booking.domain.VisitStep
import com.tana.spa.feature.booking.domain.currentVisit
import com.tana.spa.feature.booking.domain.visitStep
import com.tana.spa.shared.booking.BookingStatusDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L
private const val START = 1_000_000L

private fun booking(status: BookingStatusDto, minutes: Int = 60) = GuestBooking(
    id = "b1",
    reference = "JADE-1",
    serviceName = "Massage",
    minutes = minutes,
    therapistName = "Mai",
    startAtEpochMillis = START,
    startDayEpochDay = 20_000,
    startHour = 14.0,
    status = status,
    total = 450,
    discount = 0,
    code = null,
    declineReason = null,
    alternativeOffer = null,
)

class VisitProgressTest {

    @Test
    fun theThreeStatusesOnTheHappyPathMapToItsThreeStepsInOrder() {
        assertEquals(VisitStep.REQUESTED, visitStep(BookingStatusDto.PENDING))
        assertEquals(VisitStep.CONFIRMED, visitStep(BookingStatusDto.CONFIRMED))
        assertEquals(VisitStep.COMPLETED, visitStep(BookingStatusDto.COMPLETED))
        assertEquals(listOf(0, 1, 2), VisitStep.entries.map { it.ordinal })
    }

    @Test
    fun aVisitThatEndedOffTheTrackHasNoStepAtAll() {
        assertNull(visitStep(BookingStatusDto.DECLINED))
        assertNull(visitStep(BookingStatusDto.CANCELLED))
        assertNull(visitStep(BookingStatusDto.NO_SHOW))
    }

    @Test
    fun aConfirmedVisitIsUnderWayBetweenItsStartAndItsEnd() {
        val visit = booking(BookingStatusDto.CONFIRMED, minutes = 60)

        assertFalse(visit.inProgress(START - 1))
        assertTrue(visit.inProgress(START))
        assertTrue(visit.inProgress(START + 59 * MINUTE))
    }

    @Test
    fun theVisitStopsBeingUnderWayTheMomentItsLastMinuteIsUp() {
        val visit = booking(BookingStatusDto.CONFIRMED, minutes = 60)

        assertFalse(visit.inProgress(START + 60 * MINUTE))
        assertFalse(visit.inProgress(START + 61 * MINUTE))
    }

    @Test
    fun anUnconfirmedOrFinishedVisitIsNeverUnderWayEvenDuringItsHour() {
        val midway = START + 30 * MINUTE
        assertFalse(booking(BookingStatusDto.PENDING).inProgress(midway))
        assertFalse(booking(BookingStatusDto.CANCELLED).inProgress(midway))
        assertFalse(booking(BookingStatusDto.COMPLETED).inProgress(midway))
        assertFalse(booking(BookingStatusDto.NO_SHOW).inProgress(midway))
    }

    @Test
    fun theVisitUnderWayWinsOverOneComingUpSooner() {
        val now = START + 10 * MINUTE
        val running = booking(BookingStatusDto.CONFIRMED).copy(id = "running")
        val later = booking(BookingStatusDto.CONFIRMED).copy(id = "later", startAtEpochMillis = now + 20 * MINUTE)

        assertEquals("running", listOf(later, running).currentVisit(now)?.id)
    }

    @Test
    fun withNothingRunningTheSoonestOneAheadIsFollowed() {
        val now = START
        val soon = booking(BookingStatusDto.CONFIRMED).copy(id = "soon", startAtEpochMillis = now + 2 * MINUTE)
        val later = booking(BookingStatusDto.PENDING).copy(id = "later", startAtEpochMillis = now + 90 * MINUTE)

        assertEquals("soon", listOf(later, soon).currentVisit(now)?.id)
    }

    @Test
    fun aVisitFurtherOffThanADayIsNotFollowedYet() {
        val now = START
        val justInside = booking(BookingStatusDto.CONFIRMED).copy(startAtEpochMillis = now + 23 * 60 * MINUTE)
        val justOutside = booking(BookingStatusDto.CONFIRMED).copy(startAtEpochMillis = now + 25 * 60 * MINUTE)

        assertEquals(justInside.id, listOf(justInside).currentVisit(now)?.id)
        assertNull(listOf(justOutside).currentVisit(now))
    }

    @Test
    fun visitsThatEndedAreNeverFollowed() {
        val now = START
        val ahead = now + 30 * MINUTE
        val ended = listOf(
            booking(BookingStatusDto.CANCELLED).copy(startAtEpochMillis = ahead),
            booking(BookingStatusDto.DECLINED).copy(startAtEpochMillis = ahead),
            booking(BookingStatusDto.COMPLETED).copy(startAtEpochMillis = ahead),
            booking(BookingStatusDto.NO_SHOW).copy(startAtEpochMillis = ahead),
        )

        assertNull(ended.currentVisit(now))
    }

    @Test
    fun aVisitAlreadyPastIsNotFollowedBack() {
        val visit = booking(BookingStatusDto.CONFIRMED, minutes = 60)

        assertNull(listOf(visit).currentVisit(START + 120 * MINUTE))
    }
}
