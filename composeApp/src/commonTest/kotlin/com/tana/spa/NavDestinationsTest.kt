package com.tana.spa

import com.tana.spa.core.navigation.Routes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavDestinationsTest {
    private val labels = NavLabels("Book", "Alerts", "Offers", "Desk", "Settings")

    private fun routes(owner: Boolean) = navDestinations(owner, notificationsEnabled = true, unread = 0, labels = labels).map { it.route }

    @Test
    fun the_desk_is_offered_to_the_owner() {
        assertTrue(Routes.ADMIN_REQUESTS in routes(owner = true))
    }

    @Test
    fun a_guest_is_not_offered_the_desk() {
        assertFalse(Routes.ADMIN_REQUESTS in routes(owner = false))
    }

    @Test
    fun the_alert_badge_is_hidden_while_notifications_are_switched_off() {
        val off = navDestinations(owner = false, notificationsEnabled = false, unread = 3, labels = labels)
        assertEquals(0, off.first { it.route == Routes.NOTIFICATIONS }.badgeCount)
        val on = navDestinations(owner = false, notificationsEnabled = true, unread = 3, labels = labels)
        assertEquals(3, on.first { it.route == Routes.NOTIFICATIONS }.badgeCount)
    }
}
