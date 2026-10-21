package com.tana.spa

import com.tana.spa.core.navigation.Routes
import com.tana.spa.shared.auth.SpaPermission
import com.tana.spa.shared.auth.SpaRoles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavDestinationsTest {
    private val labels = NavLabels("Book", "Alerts", "Offers", "Desk", "Settings")

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
    fun the_alert_badge_is_hidden_while_notifications_are_switched_off() {
        val off = navDestinations(desk = false, notificationsEnabled = false, unread = 3, labels = labels)
        assertEquals(0, off.first { it.route == Routes.NOTIFICATIONS }.badgeCount)
        val on = navDestinations(desk = false, notificationsEnabled = true, unread = 3, labels = labels)
        assertEquals(3, on.first { it.route == Routes.NOTIFICATIONS }.badgeCount)
    }
}
