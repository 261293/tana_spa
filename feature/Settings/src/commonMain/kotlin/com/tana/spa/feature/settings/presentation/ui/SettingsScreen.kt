package com.tana.spa.feature.settings.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaAvatar
import com.tana.spa.core.ui.components.SpaGroupLabel
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaTextField
import com.tana.spa.core.ui.components.spaFelt
import com.tana.spa.feature.settings.presentation.NotificationPref
import com.tana.spa.feature.settings.presentation.SettingsEffect
import com.tana.spa.feature.settings.presentation.SettingsEvent
import com.tana.spa.feature.settings.presentation.SettingsState
import com.tana.spa.feature.settings.presentation.SupportedLanguages
import com.tana.spa.feature.settings.resources.Res
import com.tana.spa.feature.settings.resources.settings_cancel
import com.tana.spa.feature.settings.resources.settings_card
import com.tana.spa.feature.settings.resources.settings_card_help
import com.tana.spa.feature.settings.resources.settings_confirmations
import com.tana.spa.feature.settings.resources.settings_confirmations_help
import com.tana.spa.feature.settings.resources.settings_edit
import com.tana.spa.feature.settings.resources.settings_email
import com.tana.spa.feature.settings.resources.settings_group_account
import com.tana.spa.feature.settings.resources.settings_group_booking
import com.tana.spa.feature.settings.resources.settings_group_payment
import com.tana.spa.feature.settings.resources.settings_group_promotions
import com.tana.spa.feature.settings.resources.settings_language
import com.tana.spa.feature.settings.resources.settings_name
import com.tana.spa.feature.settings.resources.settings_offers
import com.tana.spa.feature.settings.resources.settings_offers_help
import com.tana.spa.feature.settings.resources.settings_phone
import com.tana.spa.feature.settings.resources.settings_reminder
import com.tana.spa.feature.settings.resources.settings_reminder_help
import com.tana.spa.feature.settings.resources.settings_save
import com.tana.spa.feature.settings.resources.settings_sign_out
import com.tana.spa.feature.settings.resources.settings_sms
import com.tana.spa.feature.settings.resources.settings_sms_help
import com.tana.spa.feature.settings.resources.settings_title
import com.tana.spa.feature.settings.resources.settings_title_accent
import com.tana.spa.shared.spa.initialsOf
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

/** Stateless: renders [state], reports intents through [onEvent], routes one-shot [effects]. */
@Composable
fun SettingsScreen(state: SettingsState, effects: Flow<SettingsEffect>, onEvent: (SettingsEvent) -> Unit, onSignedOut: () -> Unit) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                SettingsEffect.SignedOut -> onSignedOut()
            }
        }
    }

    SpaPage(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        maxWidth = SpaPageWidth.Reading,
    ) {
        SpaScreenHeader(
            title = stringResource(Res.string.settings_title),
            accent = stringResource(Res.string.settings_title_accent),
        )

        Group(stringResource(Res.string.settings_group_account)) {
            if (state.editing) ProfileEditor(state, onEvent) else ProfileRow(state, onEvent)
        }

        Group(stringResource(Res.string.settings_group_booking)) {
            ToggleRow(
                label = stringResource(Res.string.settings_confirmations),
                help = stringResource(Res.string.settings_confirmations_help),
                checked = state.prefs.confirmations,
                onChange = { onEvent(SettingsEvent.PrefToggled(NotificationPref.CONFIRMATIONS, it)) },
            )
            ToggleRow(
                label = stringResource(Res.string.settings_reminder),
                help = stringResource(Res.string.settings_reminder_help),
                checked = state.prefs.dayBeforeReminder,
                onChange = { onEvent(SettingsEvent.PrefToggled(NotificationPref.DAY_BEFORE_REMINDER, it)) },
            )
            ToggleRow(
                label = stringResource(Res.string.settings_sms),
                help = stringResource(Res.string.settings_sms_help, state.profile.phone),
                checked = state.prefs.textMessages,
                onChange = { onEvent(SettingsEvent.PrefToggled(NotificationPref.TEXT_MESSAGES, it)) },
            )
        }

        Group(stringResource(Res.string.settings_group_promotions)) {
            ToggleRow(
                label = stringResource(Res.string.settings_offers),
                help = stringResource(Res.string.settings_offers_help),
                checked = state.prefs.offers,
                onChange = { onEvent(SettingsEvent.PrefToggled(NotificationPref.OFFERS, it)) },
            )
        }

        Group(stringResource(Res.string.settings_group_payment)) {
            PaymentRow()
            LanguageRow(state.language) { onEvent(SettingsEvent.LanguageChanged(it)) }
        }

        SpaSecondaryButton(
            text = stringResource(Res.string.settings_sign_out),
            onClick = { onEvent(SettingsEvent.SignOut) },
            contentColor = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpaGroupLabel(title, Modifier.padding(start = 4.dp))
        // 2dp gaps rather than dividers: this palette separates rows by tone, not by lines.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

@Composable
private fun rowColors() = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surface,
    headlineColor = MaterialTheme.colorScheme.onSurface,
    supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun ProfileRow(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = state.profile.name,
                style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
            )
        },
        supportingContent = {
            Column {
                Text(state.profile.email, style = SpaTheme.textStyles.body)
                Text(state.profile.phone, style = SpaTheme.textStyles.body)
            }
        },
        leadingContent = {
            SpaAvatar(
                initials = initialsOf(state.profile.name),
                size = 52.dp,
                container = MaterialTheme.colorScheme.primaryContainer,
            )
        },
        trailingContent = {
            FilledTonalButton(onClick = { onEvent(SettingsEvent.EditProfile) }, shape = MaterialTheme.shapes.small) {
                SpaIconImage(SpaIcon.Pencil, size = 16.dp)
                Text(
                    text = stringResource(Res.string.settings_edit),
                    style = SpaTheme.textStyles.body,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        },
        colors = rowColors(),
    )
}

@Composable
private fun ProfileEditor(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().spaFelt().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpaTextField(
            label = stringResource(Res.string.settings_name),
            value = state.draft.name,
            onValueChange = { onEvent(SettingsEvent.DraftNameChanged(it)) },
            placeholder = "",
        )
        SpaTextField(
            label = stringResource(Res.string.settings_email),
            value = state.draft.email,
            onValueChange = { onEvent(SettingsEvent.DraftEmailChanged(it)) },
            placeholder = "",
        )
        SpaTextField(
            label = stringResource(Res.string.settings_phone),
            value = state.draft.phone,
            onValueChange = { onEvent(SettingsEvent.DraftPhoneChanged(it)) },
            placeholder = "",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpaSecondaryButton(
                text = stringResource(Res.string.settings_cancel),
                onClick = { onEvent(SettingsEvent.CancelEdit) },
                modifier = Modifier.weight(1f),
            )
            SpaPrimaryButton(
                text = stringResource(Res.string.settings_save),
                onClick = { onEvent(SettingsEvent.SaveProfile) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The whole row toggles, which is the Material pattern: the switch itself is not focusable. */
@Composable
private fun ToggleRow(label: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label, style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold)) },
        supportingContent = { Text(help, style = SpaTheme.textStyles.body) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    uncheckedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        },
        colors = rowColors(),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun PaymentRow() {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(Res.string.settings_card),
                style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
            )
        },
        supportingContent = { Text(stringResource(Res.string.settings_card_help), style = SpaTheme.textStyles.body) },
        leadingContent = { SpaIconImage(SpaIcon.CreditCard, size = 22.dp) },
        trailingContent = { SpaIconImage(SpaIcon.ChevronRight, size = 18.dp) },
        colors = rowColors(),
    )
}

@Composable
private fun LanguageRow(language: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(Res.string.settings_language),
                style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
            )
        },
        leadingContent = { SpaIconImage(SpaIcon.Globe, size = 22.dp) },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(language, style = SpaTheme.textStyles.body)
                    SpaIconImage(SpaIcon.ChevronDown, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    SupportedLanguages.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, style = SpaTheme.textStyles.body) },
                            onClick = {
                                onChange(option)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
        colors = rowColors(),
        modifier = Modifier.clickable(role = Role.Button) { expanded = true },
    )
}
