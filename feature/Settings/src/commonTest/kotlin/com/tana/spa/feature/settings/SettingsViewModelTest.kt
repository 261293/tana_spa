package com.tana.spa.feature.settings

import com.tana.spa.feature.settings.domain.CurrentProfile
import com.tana.spa.feature.settings.presentation.GuestProfile
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

    private val amara = GuestProfile("Amara Liu", "amara.liu@fastmail.com", "+14155550186")
    private val hieu = GuestProfile("Hieu Tran", "hieu@example.com", "+84333433969")

    private var signedOut = false
    private var signedIn: GuestProfile? = amara

    private fun viewModel() = SettingsViewModel(CurrentProfile { signedIn }, { signedOut = true })

    @Test
    fun the_profile_is_the_signed_in_account() {
        assertEquals(amara, viewModel().state.value.profile)

        signedIn = hieu
        assertEquals(hieu, viewModel().state.value.profile, "a second account must not see the first one's details")
    }

    @Test
    fun a_screen_opened_without_a_session_shows_nothing_rather_than_a_sample_guest() {
        signedIn = null

        val state = viewModel().state.value

        assertEquals(GuestProfile(), state.profile)
        assertEquals("", state.profile.name)
    }

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
