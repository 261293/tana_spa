package com.tana.spa.feature.settings.presentation

/** The signed-in account's own details; empty until the session has been read. */
data class GuestProfile(val name: String = "", val email: String = "", val phone: String = "")

data class NotificationPrefs(
    val confirmations: Boolean = true,
    val dayBeforeReminder: Boolean = true,
    val textMessages: Boolean = false,
    val offers: Boolean = true,
)

/** [draft] holds the edits until they are saved, so Cancel can put [profile] back unchanged. */
data class SettingsState(
    val profile: GuestProfile = GuestProfile(),
    val draft: GuestProfile = GuestProfile(),
    val editing: Boolean = false,
    val prefs: NotificationPrefs = NotificationPrefs(),
    val language: String = SupportedLanguages.first(),
)

/** Language names are written in the language itself, so they are the same in every locale. */
val SupportedLanguages = listOf("English", "Tiếng Việt", "Español", "中文")

enum class NotificationPref { CONFIRMATIONS, DAY_BEFORE_REMINDER, TEXT_MESSAGES, OFFERS }

sealed interface SettingsEvent {
    data object EditProfile : SettingsEvent
    data object CancelEdit : SettingsEvent
    data object SaveProfile : SettingsEvent
    data class DraftNameChanged(val value: String) : SettingsEvent
    data class DraftEmailChanged(val value: String) : SettingsEvent
    data class DraftPhoneChanged(val value: String) : SettingsEvent
    data class PrefToggled(val pref: NotificationPref, val enabled: Boolean) : SettingsEvent
    data class LanguageChanged(val language: String) : SettingsEvent
    data object SignOut : SettingsEvent
}

sealed interface SettingsEffect {
    data object SignedOut : SettingsEffect
}
