package com.tana.spa.feature.settings.presentation

import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.settings.domain.SignOutAction

/**
 * The guest's own details and notification preferences. They are not stored anywhere yet; only
 * [SignOutAction] reaches outside the screen, because the session must really end.
 */
class SettingsViewModel(private val signOutAction: SignOutAction) :
    MviViewModel<SettingsState, SettingsEvent, SettingsEffect>(SettingsState()) {

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
