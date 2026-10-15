package com.tana.spa.feature.admin

import com.tana.spa.feature.admin.presentation.AdminCodesEffect
import com.tana.spa.feature.admin.presentation.AdminCodesEvent
import com.tana.spa.feature.admin.presentation.AdminCodesViewModel
import com.tana.spa.feature.admin.presentation.DiscountKind
import com.tana.spa.feature.admin.presentation.EligibleTreatments
import com.tana.spa.shared.spa.AdminCodeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdminCodesViewModelTest {

    @Test
    fun filtering_narrows_the_table_to_one_state() {
        val vm = AdminCodesViewModel()
        vm.onEvent(AdminCodesEvent.FilterChanged(AdminCodeState.EXPIRED))

        assertTrue(vm.state.value.shown.all { it.state == AdminCodeState.EXPIRED })
        assertEquals(vm.state.value.count(AdminCodeState.EXPIRED), vm.state.value.shown.size)
    }

    @Test
    fun a_duplicate_or_empty_code_cannot_be_published() {
        val vm = AdminCodesViewModel()
        vm.onEvent(AdminCodesEvent.CreateStarted)
        assertTrue(vm.state.value.canPublish)

        vm.onEvent(AdminCodesEvent.DraftChanged(vm.state.value.draft.copy(code = "JADE20")))
        assertTrue(vm.state.value.duplicateCode)
        assertFalse(vm.state.value.canPublish)

        vm.onEvent(AdminCodesEvent.DraftChanged(vm.state.value.draft.copy(code = "NEW", value = "0")))
        assertFalse(vm.state.value.canPublish)
    }

    @Test
    fun publishing_adds_a_scheduled_code_at_the_top() = runTest {
        val vm = AdminCodesViewModel()
        vm.onEvent(AdminCodesEvent.CreateStarted)
        vm.onEvent(
            AdminCodesEvent.DraftChanged(
                vm.state.value.draft.copy(
                    code = "AUTUMNSTEAM",
                    kind = DiscountKind.AMOUNT,
                    value = "20",
                    eligible = EligibleTreatments.BODY,
                    minimumSpend = "80",
                ),
            ),
        )
        vm.onEvent(AdminCodesEvent.Publish)

        val published = vm.state.value.codes.first()
        assertEquals("AUTUMNSTEAM", published.code)
        assertEquals("$20 off", published.offer)
        assertEquals(AdminCodeState.SCHEDULED, published.state)
        assertTrue(published.eligible.endsWith("min $80"))
        assertFalse(vm.state.value.creating)
        assertIs<AdminCodesEffect.ShowPublished>(vm.effects.first())
    }

    @Test
    fun disabling_a_code_marks_it_and_notes_the_day() = runTest {
        val vm = AdminCodesViewModel()
        val live = vm.state.value.codes.first { it.state == AdminCodeState.LIVE }

        vm.onEvent(AdminCodesEvent.DisableStarted(live))
        vm.onEvent(AdminCodesEvent.DisableConfirmed)

        val updated = vm.state.value.codes.first { it.code == live.code }
        assertEquals(AdminCodeState.DISABLED, updated.state)
        assertEquals("Disabled today", updated.note)
        assertIs<AdminCodesEffect.ShowDisabled>(vm.effects.first())
    }
}
