package com.tana.spa.feature.settings.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.AppLanguage
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaAvatar
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaRowGroup
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaSegmented
import com.tana.spa.core.ui.components.SpaTextField
import com.tana.spa.core.ui.rememberSpaScrollState
import com.tana.spa.core.ui.revealOnScroll
import com.tana.spa.core.ui.spaPointer
import com.tana.spa.feature.settings.presentation.NotificationPref
import com.tana.spa.feature.settings.presentation.SettingsEffect
import com.tana.spa.feature.settings.presentation.SettingsEvent
import com.tana.spa.feature.settings.presentation.SettingsState
import com.tana.spa.feature.settings.resources.Res
import com.tana.spa.feature.settings.resources.settings_cancel
import com.tana.spa.feature.settings.resources.settings_confirmations
import com.tana.spa.feature.settings.resources.settings_confirmations_help
import com.tana.spa.feature.settings.resources.settings_edit
import com.tana.spa.feature.settings.resources.settings_email
import com.tana.spa.feature.settings.resources.settings_group_account
import com.tana.spa.feature.settings.resources.settings_group_booking
import com.tana.spa.feature.settings.resources.settings_group_language
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberSpaScrollState()),
        maxWidth = SpaPageWidth.Reading,
    ) {
        SpaScreenHeader(
            title = stringResource(Res.string.settings_title),
            accent = stringResource(Res.string.settings_title_accent),
            modifier = Modifier.revealOnScroll(),
        )

        Group(stringResource(Res.string.settings_group_account), Modifier.revealOnScroll(order = 1)) {
            if (state.editing) ProfileEditor(state, onEvent) else ProfileRow(state, onEvent)
        }

        Group(stringResource(Res.string.settings_group_booking), Modifier.revealOnScroll(order = 2)) {
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

        Group(stringResource(Res.string.settings_group_promotions), Modifier.revealOnScroll(order = 3)) {
            ToggleRow(
                label = stringResource(Res.string.settings_offers),
                help = stringResource(Res.string.settings_offers_help),
                checked = state.prefs.offers,
                onChange = { onEvent(SettingsEvent.PrefToggled(NotificationPref.OFFERS, it)) },
            )
        }

        Group(stringResource(Res.string.settings_group_language), Modifier.revealOnScroll(order = 4)) {
            LanguageRow(state.language) { onEvent(SettingsEvent.LanguageChanged(it)) }
        }

        SpaSecondaryButton(
            text = stringResource(Res.string.settings_sign_out),
            onClick = { onEvent(SettingsEvent.SignOut) },
            contentColor = MaterialTheme.colorScheme.error,
            modifier = Modifier.revealOnScroll(order = 5),
        )
    }
}

@Composable
private fun Group(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SpaRowGroup(modifier = modifier, title = title, rows = listOf { Column { content() } })
}

@Composable
private fun rowColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
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
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        trailingContent = {
            // An icon button rather than a labelled one, so a long email keeps the row's width.
            FilledTonalIconButton(onClick = { onEvent(SettingsEvent.EditProfile) }, modifier = Modifier.spaPointer()) {
                SpaIconImage(SpaIcon.Pencil, size = 20.dp, contentDescription = stringResource(Res.string.settings_edit))
            }
        },
        colors = rowColors(),
    )
}

@Composable
private fun ProfileEditor(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        },
        colors = rowColors(),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun LanguageRow(language: AppLanguage, onChange: (AppLanguage) -> Unit) {
    val options = AppLanguage.entries
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(Res.string.settings_language),
                style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
            )
        },
        leadingContent = { SpaIconImage(SpaIcon.Globe, size = 22.dp) },
        trailingContent = {
            SpaSegmented(
                options = options.map { it.label },
                selectedIndex = options.indexOf(language),
                onSelect = { onChange(options[it]) },
            )
        },
        colors = rowColors(),
    )
}
