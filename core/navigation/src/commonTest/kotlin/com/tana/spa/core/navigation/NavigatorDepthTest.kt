package com.tana.spa.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigatorDepthTest {

    @Test
    fun a_new_navigator_sits_at_the_root() {
        assertEquals(1, MutableNavigator(Routes.BOOKING).depth)
    }

    @Test
    fun navigating_goes_deeper_and_back_comes_up_again() {
        val navigator = MutableNavigator(Routes.BOOKING)
        navigator.navigate(Routes.SETTINGS)
        assertEquals(2, navigator.depth)
        navigator.back()
        assertEquals(1, navigator.depth)
        assertEquals(Routes.BOOKING, navigator.current)
    }

    @Test
    fun back_at_the_root_leaves_the_depth_alone() {
        val navigator = MutableNavigator(Routes.BOOKING)
        navigator.back()
        assertEquals(1, navigator.depth)
    }

    @Test
    fun replacing_the_stack_returns_to_the_root() {
        val navigator = MutableNavigator(Routes.BOOKING)
        navigator.navigate(Routes.SETTINGS)
        navigator.navigate(Routes.MY_BOOKINGS)
        navigator.replaceAll(Routes.AUTH)
        assertEquals(1, navigator.depth)
    }
}
