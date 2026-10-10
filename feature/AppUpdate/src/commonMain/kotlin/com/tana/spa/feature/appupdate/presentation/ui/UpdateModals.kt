package com.tana.spa.feature.appupdate.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.feature.appupdate.domain.UpdateVerdict
import com.tana.spa.feature.appupdate.presentation.AppUpdateEffect
import com.tana.spa.feature.appupdate.presentation.AppUpdateEvent
import com.tana.spa.feature.appupdate.presentation.AppUpdateState
import com.tana.spa.feature.appupdate.resources.Res
import com.tana.spa.feature.appupdate.resources.update_action
import com.tana.spa.feature.appupdate.resources.update_force_body
import com.tana.spa.feature.appupdate.resources.update_force_title
import com.tana.spa.feature.appupdate.resources.update_later
import com.tana.spa.feature.appupdate.resources.update_optional_body
import com.tana.spa.feature.appupdate.resources.update_optional_title
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

@Composable
fun UpdateModals(state: AppUpdateState, effects: Flow<AppUpdateEffect>, onEvent: (AppUpdateEvent) -> Unit, onOpenStore: (String) -> Unit) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is AppUpdateEffect.OpenStore -> onOpenStore(effect.url)
            }
        }
    }
    if (!state.visible) return

    when (val verdict = state.verdict) {
        UpdateVerdict.None -> Unit

        is UpdateVerdict.Forced -> UpdateDialog(
            title = stringResource(Res.string.update_force_title),
            body = stringResource(Res.string.update_force_body, verdict.latestVersion.toString()),
            dismissible = false,
            onDismiss = {},
        ) {
            UpdateAction(onEvent)
        }

        is UpdateVerdict.Optional -> UpdateDialog(
            title = stringResource(Res.string.update_optional_title),
            body = stringResource(Res.string.update_optional_body, verdict.latestVersion.toString()),
            dismissible = true,
            onDismiss = { onEvent(AppUpdateEvent.Dismissed) },
        ) {
            UpdateAction(onEvent)
            SpaSecondaryButton(
                text = stringResource(Res.string.update_later),
                onClick = { onEvent(AppUpdateEvent.Dismissed) },
            )
        }
    }
}

@Composable
private fun UpdateAction(onEvent: (AppUpdateEvent) -> Unit) {
    SpaPrimaryButton(
        text = stringResource(Res.string.update_action),
        onClick = { onEvent(AppUpdateEvent.UpdateClicked) },
    )
}

/**
 * [dismissible] false also stops the system back gesture, which is the whole point of a forced
 * prompt: a build below the supported floor must not reach the app behind it.
 */
@Composable
private fun UpdateDialog(title: String, body: String, dismissible: Boolean, onDismiss: () -> Unit, actions: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    style = SpaTheme.textStyles.screenTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = SpaTheme.textStyles.bodyRelaxed,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                actions()
            }
        }
    }
}
