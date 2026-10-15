package com.tana.spa.feature.booking

import com.tana.spa.feature.booking.presentation.BookingEffect
import com.tana.spa.feature.booking.presentation.BookingEvent
import com.tana.spa.feature.booking.presentation.BookingViewModel
import com.tana.spa.shared.spa.PromoCheck
import com.tana.spa.shared.spa.SpaCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookingViewModelTest {

    private fun viewModel() = BookingViewModel()

    @Test
    fun applying_an_eligible_code_discounts_the_total() {
        val vm = viewModel()
        vm.onEvent(BookingEvent.ServiceSelected("jade-facial"))
        vm.onEvent(BookingEvent.CodeChanged("jade20"))
        vm.onEvent(BookingEvent.ApplyCode)

        val state = vm.state.value
        assertIs<PromoCheck.Applied>(state.check)
        assertEquals(120 - 24, state.totalUsd)
    }

    @Test
    fun switching_treatment_rechecks_an_applied_code() {
        val vm = viewModel()
        vm.onEvent(BookingEvent.ServiceSelected("jade-facial"))
        vm.onEvent(BookingEvent.CodeChanged("JADE20"))
        vm.onEvent(BookingEvent.ApplyCode)

        vm.onEvent(BookingEvent.ServiceSelected("stone"))

        // The facial-only code must not keep discounting a massage.
        assertIs<PromoCheck.Rejected>(vm.state.value.check)
        assertEquals(165, vm.state.value.totalUsd)
    }

    @Test
    fun a_code_from_the_offers_screen_picks_a_treatment_it_applies_to() {
        val vm = viewModel()
        assertEquals("stone", vm.state.value.serviceId) // a massage by default

        vm.onEvent(BookingEvent.CodeOffered("jade20"))

        assertEquals("jade-facial", vm.state.value.serviceId)
        assertIs<PromoCheck.Applied>(vm.state.value.check)
    }

    @Test
    fun editing_the_code_clears_the_previous_verdict() {
        val vm = viewModel()
        vm.onEvent(BookingEvent.CodeChanged("JADE20"))
        vm.onEvent(BookingEvent.ApplyCode)
        vm.onEvent(BookingEvent.CodeChanged("JADE2"))

        assertNull(vm.state.value.check)
    }

    @Test
    fun choosing_another_day_drops_the_chosen_time() {
        val vm = viewModel()
        vm.onEvent(BookingEvent.SlotSelected("2:30 PM"))
        vm.onEvent(BookingEvent.DaySelected(SpaCatalog.today.plusDays(4)))

        assertNull(vm.state.value.slot)
        assertEquals(SpaCatalog.today.plusDays(4), vm.state.value.day)
    }

    @Test
    fun a_request_needs_treatment_therapist_and_time() {
        val vm = viewModel()
        assertTrue(!vm.state.value.canRequest)

        vm.onEvent(BookingEvent.SlotSelected("4:00 PM"))

        assertTrue(vm.state.value.canRequest)
        vm.onEvent(BookingEvent.Submit)
        assertTrue(vm.state.value.requestSent)
    }

    @Test
    fun opening_notifications_closes_the_confirmation_and_navigates() = runTest {
        val vm = viewModel()
        vm.onEvent(BookingEvent.SlotSelected("4:00 PM"))
        vm.onEvent(BookingEvent.Submit)

        vm.onEvent(BookingEvent.OpenNotifications)

        assertEquals(BookingEffect.NavigateToNotifications, vm.effects.first())
        assertTrue(!vm.state.value.requestSent)
    }
}
