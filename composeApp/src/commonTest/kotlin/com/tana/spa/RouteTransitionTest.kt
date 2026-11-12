package com.tana.spa

import com.tana.spa.common.FeaturesRegistry
import com.tana.spa.core.navigation.Routes
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteTransitionTest {
    private val labels = NavLabels("Treatments", "Book", "Alerts", "Offers", "Desk", "Settings")

    @Test
    fun every_destination_in_the_bar_fades_through_rather_than_sliding() {
        for (desk in listOf(false, true)) {
            val destinations = navDestinations(desk, notificationsEnabled = true, unread = 0, labels = labels)
            for (destination in destinations) {
                assertTrue(destination.route in TOP_LEVEL_ROUTES, destination.route)
            }
        }
    }

    @Test
    fun a_screen_reached_from_another_screen_slides_along_the_shared_axis() {
        for (route in listOf(Routes.SERVICE_DETAIL, Routes.BOOKING_WHEN, Routes.MY_BOOKINGS)) {
            assertFalse(route in TOP_LEVEL_ROUTES, route)
        }
    }

    @Test
    fun no_top_level_route_is_unknown_to_the_registry() {
        for (route in TOP_LEVEL_ROUTES) {
            assertTrue(FeaturesRegistry.entries.any { it.route == route }, route)
        }
    }
}
