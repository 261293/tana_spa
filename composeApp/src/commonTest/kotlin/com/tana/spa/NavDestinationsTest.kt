package com.tana.spa

import com.tana.spa.core.navigation.Routes
import com.tana.spa.shared.auth.SpaPermission
import com.tana.spa.shared.auth.SpaRoles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val MAX_BAR_DESTINATIONS = 5

class NavDestinationsTest {
    private val labels = NavLabels("Book", "Visits", "Alerts", "Offers", "Desk", "Settings")

    private fun routes(desk: Boolean) = navDestinations(desk, notificationsEnabled = true, unread = 0, labels = labels).map { it.route }

    @Test
    fun the_desk_is_offered_to_whoever_works_it() {
        assertTrue(Routes.ADMIN_REQUESTS in routes(desk = true))
    }

    @Test
    fun a_guest_is_not_offered_the_desk() {
        assertFalse(Routes.ADMIN_REQUESTS in routes(desk = false))
    }

    @Test
    fun staff_the_owner_and_an_administrator_all_work_the_desk_but_a_guest_does_not() {
        for (role in listOf(SpaRoles.STAFF, SpaRoles.OWNER, SpaRoles.ADMIN)) {
            assertTrue(SpaRoles.has(role, SpaPermission.MANAGE_BOOKINGS), role)
        }
        assertFalse(SpaRoles.has(SpaRoles.CUSTOMER, SpaPermission.MANAGE_BOOKINGS))
        assertFalse(SpaRoles.has("", SpaPermission.MANAGE_BOOKINGS))
    }

    @Test
    fun the_alerts_destination_is_gone_while_notifications_are_switched_off() {
        val off = navDestinations(desk = false, notificationsEnabled = false, unread = 3, labels = labels)
        assertTrue(off.none { it.route == Routes.NOTIFICATIONS })
        val on = navDestinations(desk = false, notificationsEnabled = true, unread = 3, labels = labels)
        assertEquals(3, on.first { it.route == Routes.NOTIFICATIONS }.badgeCount)
    }

    @Test
    fun a_guest_s_destinations_start_with_booking_then_their_visits() {
        assertEquals(listOf(Routes.BOOKING, Routes.MY_BOOKINGS), routes(desk = false).take(2))
    }

    @Test
    fun the_visits_stand_down_for_whoever_works_the_desk() {
        assertFalse(Routes.MY_BOOKINGS in routes(desk = true))
        assertEquals(Routes.ADMIN_REQUESTS, routes(desk = true).single { it == Routes.ADMIN_REQUESTS })
    }

    /** Material's bottom bar is specified for three to five destinations, whoever is signed in. */
    @Test
    fun no_one_sees_more_destinations_than_the_bar_holds() {
        for (desk in listOf(false, true)) {
            assertTrue(routes(desk).size <= MAX_BAR_DESTINATIONS, routes(desk).toString())
        }
    }
}
