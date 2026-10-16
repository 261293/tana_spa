package com.tana.spa

import com.tana.spa.core.navigation.Routes
import com.tana.spa.core.navigation.withArg
import com.tana.spa.feature.auth.domain.model.UserRoleName
import kotlin.test.Test
import kotlin.test.assertEquals

class RestoredRouteTest {

    @Test
    fun without_a_session_every_address_opens_sign_in() {
        assertEquals(Routes.AUTH, restoredRoute(Routes.SETTINGS, role = null))
    }

    @Test
    fun a_guest_comes_back_to_the_screen_the_address_names() {
        assertEquals(Routes.SETTINGS, restoredRoute(Routes.SETTINGS, UserRoleName.CUSTOMER))
    }

    @Test
    fun the_argument_in_the_address_survives_the_reload() {
        val route = Routes.SERVICE_DETAIL.withArg(Routes.ARG_SERVICE, "warm-stone-massage")
        assertEquals(route, restoredRoute(route, UserRoleName.CUSTOMER))
    }

    @Test
    fun a_guest_is_not_dropped_into_the_console() {
        assertEquals(Routes.BOOKING, restoredRoute(Routes.ADMIN_REQUESTS, UserRoleName.CUSTOMER))
    }

    @Test
    fun the_owner_comes_back_to_the_console() {
        assertEquals(Routes.ADMIN_REQUESTS, restoredRoute(Routes.ADMIN_REQUESTS, UserRoleName.OWNER))
    }

    @Test
    fun an_address_this_build_knows_no_screen_for_opens_booking() {
        assertEquals(Routes.BOOKING, restoredRoute("somethingElse", UserRoleName.CUSTOMER))
    }
}
