package com.tana.spa.feature.settings.presentation

import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.settings.domain.CurrentProfile
import com.tana.spa.feature.settings.domain.SignOutAction

/**
 * The signed-in account's details and notification preferences. Edits and preferences stay on the
 * device - there is no endpoint that changes a profile yet - but what is shown comes from the
 * session, so two accounts never see each other's details.
 */
class SettingsViewModel(private val currentProfile: CurrentProfile, private val signOutAction: SignOutAction) :
    MviViewModel<SettingsState, SettingsEvent, SettingsEffect>(SettingsState()) {

    init {
        val profile = currentProfile.load() ?: GuestProfile()
        setState { copy(profile = profile, draft = profile) }
    }

    override fun handleEvent(event: SettingsEvent) = when (event) {
        SettingsEvent.EditProfile -> setState { copy(editing = true, draft = profile) }

        SettingsEvent.CancelEdit -> setState { copy(editing = false, draft = profile) }

        SettingsEvent.SaveProfile -> setState { copy(editing = false, profile = draft) }

        is SettingsEvent.DraftNameChanged -> setState { copy(draft = draft.copy(name = event.value)) }

        is SettingsEvent.DraftEmailChanged -> setState { copy(draft = draft.copy(email = event.value)) }

        is SettingsEvent.DraftPhoneChanged -> setState { copy(draft = draft.copy(phone = event.value)) }

        is SettingsEvent.PrefToggled -> setState {
            copy(
                prefs = when (event.pref) {
                    NotificationPref.CONFIRMATIONS -> prefs.copy(confirmations = event.enabled)
                    NotificationPref.DAY_BEFORE_REMINDER -> prefs.copy(dayBeforeReminder = event.enabled)
                    NotificationPref.TEXT_MESSAGES -> prefs.copy(textMessages = event.enabled)
                    NotificationPref.OFFERS -> prefs.copy(offers = event.enabled)
                },
            )
        }

        is SettingsEvent.LanguageChanged -> setState { copy(language = event.language) }

        SettingsEvent.SignOut -> {
            AppLog.i(logTag) { "signing out" }
            signOutAction.signOut()
            sendEffect(SettingsEffect.SignedOut)
        }
    }
}
