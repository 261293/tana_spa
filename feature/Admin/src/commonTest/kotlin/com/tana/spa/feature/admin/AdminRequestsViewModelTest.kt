package com.tana.spa.feature.admin

import com.tana.spa.feature.admin.presentation.AdminRequestsEffect
import com.tana.spa.feature.admin.presentation.AdminRequestsEvent
import com.tana.spa.feature.admin.presentation.AdminRequestsViewModel
import com.tana.spa.feature.admin.presentation.AlternativeOffer
import com.tana.spa.feature.admin.presentation.DeclineReason
import com.tana.spa.shared.spa.RequestStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminRequestsViewModelTest {

    @Test
    fun confirming_moves_the_queue_on_to_the_next_one_waiting() = runTest {
        val vm = AdminRequestsViewModel()
        val first = vm.state.value.selectedId

        vm.onEvent(AdminRequestsEvent.Confirmed(first))

        val state = vm.state.value
        assertEquals(RequestStatus.CONFIRMED, state.requests.first { it.id == first }.status)
        assertNotEquals(first, state.selectedId)
        assertEquals(RequestStatus.PENDING, state.requests.first { it.id == state.selectedId }.status)
        assertIs<AdminRequestsEffect.ShowConfirmed>(vm.effects.first())
    }

    @Test
    fun a_clashing_request_opens_the_decline_dialog_on_the_overlap_reason() {
        val vm = AdminRequestsViewModel()
        val clashing = vm.state.value.requests.first { it.conflict != null }

        vm.onEvent(AdminRequestsEvent.DeclineStarted(clashing.id))

        assertEquals(clashing, vm.state.value.declining)
        assertEquals(DeclineReason.OVERLAP, vm.state.value.reason)
        assertEquals(AlternativeOffer.NONE, vm.state.value.alternative)
    }

    @Test
    fun declining_records_the_decision_and_says_whether_a_time_was_offered() = runTest {
        val vm = AdminRequestsViewModel()
        val target = vm.state.value.requests.first()

        vm.onEvent(AdminRequestsEvent.DeclineStarted(target.id))
        vm.onEvent(AdminRequestsEvent.AlternativeChosen(AlternativeOffer.TODAY_WITH_MAI))
        vm.onEvent(AdminRequestsEvent.DeclineConfirmed)

        assertEquals(RequestStatus.DECLINED, vm.state.value.requests.first { it.id == target.id }.status)
        assertNull(vm.state.value.declining)
        val effect = vm.effects.first()
        assertIs<AdminRequestsEffect.ShowDeclined>(effect)
        assertTrue(effect.withAlternative)
    }

    @Test
    fun dismissing_the_dialog_leaves_the_request_waiting() {
        val vm = AdminRequestsViewModel()
        val target = vm.state.value.requests.first()

        vm.onEvent(AdminRequestsEvent.DeclineStarted(target.id))
        vm.onEvent(AdminRequestsEvent.DeclineDismissed)

        assertNull(vm.state.value.declining)
        assertEquals(RequestStatus.PENDING, vm.state.value.requests.first { it.id == target.id }.status)
    }
}
