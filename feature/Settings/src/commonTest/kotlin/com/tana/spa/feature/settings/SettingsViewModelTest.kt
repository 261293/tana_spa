package com.tana.spa.feature.settings

import com.tana.spa.feature.settings.presentation.NotificationPref
import com.tana.spa.feature.settings.presentation.SettingsEffect
import com.tana.spa.feature.settings.presentation.SettingsEvent
import com.tana.spa.feature.settings.presentation.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsViewModelTest {

    private var signedOut = false
    private fun viewModel() = SettingsViewModel { signedOut = true }

    @Test
    fun cancelling_an_edit_restores_the_saved_profile() {
        val vm = viewModel()
        val original = vm.state.value.profile.name

        vm.onEvent(SettingsEvent.EditProfile)
        vm.onEvent(SettingsEvent.DraftNameChanged("Someone Else"))
        vm.onEvent(SettingsEvent.CancelEdit)

        assertEquals(original, vm.state.value.profile.name)
        assertEquals(original, vm.state.value.draft.name)
        assertFalse(vm.state.value.editing)
    }

    @Test
    fun saving_an_edit_keeps_the_new_profile() {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.EditProfile)
        vm.onEvent(SettingsEvent.DraftNameChanged("Amara L."))
        vm.onEvent(SettingsEvent.SaveProfile)

        assertEquals("Amara L.", vm.state.value.profile.name)
        assertFalse(vm.state.value.editing)
    }

    @Test
    fun each_preference_toggles_on_its_own() {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.PrefToggled(NotificationPref.TEXT_MESSAGES, true))

        assertTrue(vm.state.value.prefs.textMessages)
        assertTrue(vm.state.value.prefs.confirmations)
        assertTrue(vm.state.value.prefs.offers)
    }

    @Test
    fun signing_out_ends_the_session_before_navigating() = runTest {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.SignOut)

        assertTrue(signedOut)
        assertEquals(SettingsEffect.SignedOut, vm.effects.first())
    }
}
