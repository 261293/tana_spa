package com.tana.spa.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigatorDepthTest {

    @Test
    fun theRootIsDepthOne() {
        assertEquals(1, MutableNavigator(Routes.BOOKING).depth)
    }

    @Test
    fun navigatingDeeperAndComingBackAgainRetracesTheDepth() {
        val navigator = MutableNavigator(Routes.BOOKING)

        navigator.navigate(Routes.SERVICE_DETAIL)
        assertEquals(2, navigator.depth)

        navigator.navigate(Routes.MY_BOOKINGS)
        assertEquals(3, navigator.depth)

        navigator.back()
        assertEquals(2, navigator.depth)
        assertEquals(Routes.SERVICE_DETAIL, navigator.current)
    }

    @Test
    fun backAtTheRootChangesNothing() {
        val navigator = MutableNavigator(Routes.AUTH)

        navigator.back()

        assertEquals(1, navigator.depth)
        assertEquals(Routes.AUTH, navigator.current)
    }

    @Test
    fun replaceAllReturnsToDepthOneSoBackCannotLeadOut() {
        val navigator = MutableNavigator(Routes.AUTH)
        navigator.navigate(Routes.SIGNUP)
        navigator.navigate(Routes.VERIFY_PHONE)

        navigator.replaceAll(Routes.BOOKING)

        assertEquals(1, navigator.depth)
        assertEquals(Routes.BOOKING, navigator.current)
    }

    @Test
    fun anArgumentOnTheRouteStillCountsAsOneEntry() {
        val navigator = MutableNavigator(Routes.BOOKING)

        navigator.navigate(Routes.BOOKING.withArg(Routes.ARG_CODE, "JADE20"))

        assertEquals(2, navigator.depth)
        assertEquals(Routes.BOOKING, navigator.current.routeName())
    }
}
