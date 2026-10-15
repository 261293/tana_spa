package com.tana.spa.feature.promotions

import com.tana.spa.feature.promotions.presentation.PromotionsEffect
import com.tana.spa.feature.promotions.presentation.PromotionsEvent
import com.tana.spa.feature.promotions.presentation.PromotionsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromotionsViewModelTest {

    @Test
    fun a_live_code_is_recognised_whatever_the_casing() {
        val vm = PromotionsViewModel()
        vm.onEvent(PromotionsEvent.EntryChanged(" midweek15 "))
        vm.onEvent(PromotionsEvent.CheckEntry)

        assertEquals("MIDWEEK15", vm.state.value.checked?.code)
        assertTrue(!vm.state.value.checkFailed)
    }

    @Test
    fun an_unknown_code_is_reported_as_failed() {
        val vm = PromotionsViewModel()
        vm.onEvent(PromotionsEvent.EntryChanged("NOPE"))
        vm.onEvent(PromotionsEvent.CheckEntry)

        assertNull(vm.state.value.checked)
        assertTrue(vm.state.value.checkFailed)
    }

    @Test
    fun editing_the_entry_clears_the_previous_verdict() {
        val vm = PromotionsViewModel()
        vm.onEvent(PromotionsEvent.EntryChanged("JADE20"))
        vm.onEvent(PromotionsEvent.CheckEntry)
        vm.onEvent(PromotionsEvent.EntryChanged("JADE2"))

        assertNull(vm.state.value.checked)
        assertTrue(!vm.state.value.checkFailed)
    }

    @Test
    fun using_a_code_asks_for_the_booking_screen() = runTest {
        val vm = PromotionsViewModel()
        vm.onEvent(PromotionsEvent.UseCode("JADE20"))

        assertEquals(PromotionsEffect.OpenBooking("JADE20"), vm.effects.first())
    }
}
