package com.tana.spa

import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.withArg
import com.tana.spa.shared.auth.SpaRoles
import kotlin.test.Test
import kotlin.test.assertEquals

class RestoredRouteTest {

    @Test
    fun without_a_session_every_address_opens_sign_in() {
        assertEquals(Routes.AUTH, restoredRoute(Routes.SETTINGS, role = null))
    }

    @Test
    fun a_guest_comes_back_to_the_screen_the_address_names() {
        assertEquals(Routes.SETTINGS, restoredRoute(Routes.SETTINGS, SpaRoles.CUSTOMER))
    }

    @Test
    fun the_argument_in_the_address_survives_the_reload() {
        val route = Routes.SERVICE_DETAIL.withArg(Routes.ARG_SERVICE, "warm-stone-massage")
        assertEquals(route, restoredRoute(route, SpaRoles.CUSTOMER))
    }

    @Test
    fun a_guest_is_not_dropped_into_the_console() {
        assertEquals(Routes.HOME, restoredRoute(Routes.ADMIN_REQUESTS, SpaRoles.CUSTOMER))
    }

    @Test
    fun the_owner_comes_back_to_the_console() {
        assertEquals(Routes.ADMIN_REQUESTS, restoredRoute(Routes.ADMIN_REQUESTS, SpaRoles.OWNER))
    }

    @Test
    fun an_address_this_build_knows_no_screen_for_opens_home() {
        assertEquals(Routes.HOME, restoredRoute("somethingElse", SpaRoles.CUSTOMER))
    }

    @Test
    fun staff_restoring_a_booking_address_land_at_home() {
        assertEquals(Routes.HOME, restoredRoute(Routes.BOOKING, SpaRoles.STAFF))
        assertEquals(Routes.HOME, restoredRoute(Routes.MY_BOOKINGS, SpaRoles.STAFF))
        assertEquals(Routes.ADMIN_REQUESTS, restoredRoute(Routes.ADMIN_REQUESTS, SpaRoles.STAFF))
    }
}
