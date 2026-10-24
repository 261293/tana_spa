package com.tana.spa.feature.admin.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaFilterChip
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.core.ui.components.SpaStatusPill
import com.tana.spa.core.ui.components.spaCork
import com.tana.spa.feature.admin.presentation.AdminCodesEffect
import com.tana.spa.feature.admin.presentation.AdminCodesEvent
import com.tana.spa.feature.admin.presentation.AdminCodesState
import com.tana.spa.feature.admin.resources.Res
import com.tana.spa.feature.admin.resources.admin_codes_create
import com.tana.spa.feature.admin.resources.admin_codes_empty
import com.tana.spa.feature.admin.resources.admin_codes_eyebrow
import com.tana.spa.feature.admin.resources.admin_codes_subtitle
import com.tana.spa.feature.admin.resources.admin_codes_title
import com.tana.spa.feature.admin.resources.admin_codes_title_accent
import com.tana.spa.feature.admin.resources.admin_column_code
import com.tana.spa.feature.admin.resources.admin_column_eligible
import com.tana.spa.feature.admin.resources.admin_column_offer
import com.tana.spa.feature.admin.resources.admin_column_state
import com.tana.spa.feature.admin.resources.admin_column_usage
import com.tana.spa.feature.admin.resources.admin_column_valid
import com.tana.spa.feature.admin.resources.admin_disable_body
import com.tana.spa.feature.admin.resources.admin_disable_code
import com.tana.spa.feature.admin.resources.admin_disable_confirm
import com.tana.spa.feature.admin.resources.admin_disable_keep
import com.tana.spa.feature.admin.resources.admin_disable_title
import com.tana.spa.feature.admin.resources.admin_disabled_toast
import com.tana.spa.feature.admin.resources.admin_edit_code
import com.tana.spa.feature.admin.resources.admin_filter_all
import com.tana.spa.feature.admin.resources.admin_filter_state
import com.tana.spa.feature.admin.resources.admin_published_toast
import com.tana.spa.feature.admin.resources.admin_usage_left
import com.tana.spa.feature.admin.resources.admin_usage_of_limit
import com.tana.spa.feature.admin.resources.admin_usage_percent
import com.tana.spa.feature.admin.resources.admin_usage_used
import com.tana.spa.feature.admin.resources.admin_valid_range
import com.tana.spa.shared.spa.AdminCode
import com.tana.spa.shared.spa.AdminCodeState
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private const val USAGE_WARNING_PERCENT = 90

/** Redemptions this month, as reported by the console's billing summary. */
private const val REDEMPTIONS_THIS_MONTH = 287
private const val DISCOUNTED_THIS_MONTH = "4,310"

/** Stateless: renders [state], reports intents through [onEvent], routes one-shot [effects]. */
@Composable
fun AdminCodesScreen(
    state: AdminCodesState,
    effects: Flow<AdminCodesEffect>,
    onEvent: (AdminCodesEvent) -> Unit,
    pendingRequests: Int,
    onOpenRequests: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenGuestApp: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            val message = when (effect) {
                is AdminCodesEffect.ShowPublished -> getString(Res.string.admin_published_toast, effect.code, effect.starts)
                is AdminCodesEffect.ShowDisabled -> getString(Res.string.admin_disabled_toast, effect.code)
            }
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    AdminScaffold(
        section = AdminSection.CODES,
        pendingCount = pendingRequests,
        onSelect = {
            when (it) {
                AdminSection.REQUESTS -> onOpenRequests()
                AdminSection.MENU -> onOpenMenu()
                AdminSection.CODES -> Unit
            }
        },
        onOpenGuestApp = onOpenGuestApp,
    ) { wide ->
        Box(Modifier.fillMaxSize()) {
            SpaPage(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                maxWidth = SpaPageWidth.Full,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SpaScreenHeader(
                    eyebrow = stringResource(Res.string.admin_codes_eyebrow),
                    title = stringResource(Res.string.admin_codes_title),
                    accent = stringResource(Res.string.admin_codes_title_accent),
                    subtitle = stringResource(
                        Res.string.admin_codes_subtitle,
                        state.liveCount,
                        REDEMPTIONS_THIS_MONTH,
                        DISCOUNTED_THIS_MONTH,
                    ),
                )
                SpaPrimaryButton(
                    text = stringResource(Res.string.admin_codes_create),
                    onClick = { onEvent(AdminCodesEvent.CreateStarted) },
                    modifier = Modifier.widthIn(max = 260.dp),
                )
                Filters(state, onEvent)
                if (state.shown.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.admin_codes_empty),
                        style = SpaTheme.textStyles.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                } else if (wide) {
                    CodesTable(state, onEvent)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.shown.forEach { code -> CodeCard(code, onEvent) }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (state.creating) CreateCodePanel(state, onEvent)
    state.disabling?.let { DisableDialog(it, onEvent) }
}

@Composable
private fun Filters(state: AdminCodesState, onEvent: (AdminCodesEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpaFilterChip(
            label = stringResource(Res.string.admin_filter_all, state.codes.size),
            selected = state.filter == null,
            onClick = { onEvent(AdminCodesEvent.FilterChanged(null)) },
        )
        AdminCodeState.entries.forEach { codeState ->
            SpaFilterChip(
                label = stringResource(Res.string.admin_filter_state, codeState.label, state.count(codeState)),
                selected = state.filter == codeState,
                onClick = { onEvent(AdminCodesEvent.FilterChanged(codeState)) },
            )
        }
    }
}

@Composable
private fun CodesTable(state: AdminCodesState, onEvent: (AdminCodesEvent) -> Unit) {
    SpaCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeaderCell(stringResource(Res.string.admin_column_code), Modifier.width(140.dp))
                HeaderCell(stringResource(Res.string.admin_column_offer), Modifier.width(90.dp))
                HeaderCell(stringResource(Res.string.admin_column_eligible), Modifier.weight(1.4f))
                HeaderCell(stringResource(Res.string.admin_column_valid), Modifier.width(130.dp))
                HeaderCell(stringResource(Res.string.admin_column_usage), Modifier.weight(1f))
                HeaderCell(stringResource(Res.string.admin_column_state), Modifier.width(104.dp))
                Box(Modifier.width(80.dp))
            }
            state.shown.forEach { code -> TableRow(code, onEvent) }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SpaTheme.textStyles.label.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun TableRow(code: AdminCode, onEvent: (AdminCodesEvent) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = code.code,
                style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(140.dp),
            )
            Text(
                text = code.offer,
                style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(90.dp),
            )
            Column(Modifier.weight(1.4f)) {
                Text(code.eligible, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurface)
                code.note?.let {
                    Text(
                        text = it,
                        style = SpaTheme.textStyles.caption,
                        color = if (code.state == AdminCodeState.DISABLED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            SpaTheme.extraColors.warning
                        },
                    )
                }
            }
            Text(
                text = stringResource(Res.string.admin_valid_range, code.starts, code.ends),
                style = SpaTheme.textStyles.body,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(130.dp),
            )
            Usage(code, Modifier.weight(1f))
            Box(Modifier.width(104.dp)) { StateTag(code.state) }
            RowActions(code, onEvent, Modifier.width(80.dp))
        }
    }
}

@Composable
private fun CodeCard(code: AdminCode, onEvent: (AdminCodesEvent) -> Unit) {
    SpaCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        ListItem(
            overlineContent = { StateTag(code.state) },
            headlineContent = {
                Text(code.code, style = SpaTheme.textStyles.cardTitle, color = MaterialTheme.colorScheme.onSurface)
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${code.offer} · ${code.eligible}",
                        style = SpaTheme.textStyles.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(Res.string.admin_valid_range, code.starts, code.ends),
                        style = SpaTheme.textStyles.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Usage(code)
                }
            },
            trailingContent = { RowActions(code, onEvent) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun Usage(code: AdminCode, modifier: Modifier = Modifier) {
    val limit = code.limit
    if (limit == null) {
        Text(
            text = stringResource(Res.string.admin_usage_used, code.used),
            style = SpaTheme.textStyles.body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )
        return
    }
    val percent = (code.used * 100) / limit
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(Res.string.admin_usage_of_limit, code.used, limit),
                style = SpaTheme.textStyles.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (percent >= USAGE_WARNING_PERCENT && code.state == AdminCodeState.LIVE) {
                Text(
                    text = stringResource(Res.string.admin_usage_left, limit - code.used),
                    style = SpaTheme.textStyles.caption,
                    color = SpaTheme.extraColors.warning,
                )
            }
        }
        val description = stringResource(Res.string.admin_usage_percent, percent)
        LinearProgressIndicator(
            progress = { percent / 100f },
            color = if (percent >= USAGE_WARNING_PERCENT) SpaTheme.extraColors.warning else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        )
    }
}

@Composable
private fun StateTag(state: AdminCodeState) {
    val container: Color
    val content: Color
    when (state) {
        AdminCodeState.LIVE -> {
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.primary
        }

        AdminCodeState.SCHEDULED -> {
            container = MaterialTheme.colorScheme.surfaceContainerHigh
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }

        AdminCodeState.EXPIRED -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }

        AdminCodeState.DISABLED -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
        }
    }
    SpaStatusPill(text = state.label, container = container, content = content)
}

@Composable
private fun RowActions(code: AdminCode, onEvent: (AdminCodesEvent) -> Unit, modifier: Modifier = Modifier) {
    val editable = code.state != AdminCodeState.EXPIRED
    val disableable = code.state != AdminCodeState.EXPIRED && code.state != AdminCodeState.DISABLED
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalIconButton(
            onClick = {},
            enabled = editable,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            SpaIconImage(
                icon = SpaIcon.Pencil,
                size = 18.dp,
                contentDescription = stringResource(Res.string.admin_edit_code, code.code),
            )
        }
        FilledTonalIconButton(
            onClick = { onEvent(AdminCodesEvent.DisableStarted(code)) },
            enabled = disableable,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            SpaIconImage(
                icon = SpaIcon.Prohibit,
                size = 18.dp,
                contentDescription = stringResource(Res.string.admin_disable_code, code.code),
            )
        }
    }
}

@Composable
private fun DisableDialog(code: AdminCode, onEvent: (AdminCodesEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(AdminCodesEvent.DisableDismissed) },
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = stringResource(Res.string.admin_disable_title, code.code),
                style = SpaTheme.textStyles.cardTitle,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.admin_disable_body),
                style = SpaTheme.textStyles.bodyRelaxed,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(AdminCodesEvent.DisableConfirmed) }) {
                Text(
                    text = stringResource(Res.string.admin_disable_confirm),
                    style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(AdminCodesEvent.DisableDismissed) }) {
                Text(
                    text = stringResource(Res.string.admin_disable_keep),
                    style = SpaTheme.textStyles.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}
