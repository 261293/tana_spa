package com.tana.spa.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteArgsTest {
    @Test
    fun an_argument_survives_a_round_trip() {
        val route = Routes.BOOKING.withArg(Routes.ARG_CODE, "JADE20")
        assertEquals("booking?code=JADE20", route)
        assertEquals(Routes.BOOKING, route.routeName())
        assertEquals("JADE20", route.routeArg(Routes.ARG_CODE))
    }

    @Test
    fun a_plain_route_has_no_argument() {
        assertEquals(Routes.BOOKING, Routes.BOOKING.routeName())
        assertNull(Routes.BOOKING.routeArg(Routes.ARG_CODE))
        assertNull("booking?code=".routeArg(Routes.ARG_CODE))
        assertNull("booking?other=1".routeArg(Routes.ARG_CODE))
    }
}
