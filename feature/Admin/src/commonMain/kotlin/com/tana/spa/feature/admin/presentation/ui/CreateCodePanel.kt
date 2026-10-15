package com.tana.spa.feature.admin.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPebbleShape
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaCodeChip
import com.tana.spa.core.ui.components.SpaFilterChip
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaTextField
import com.tana.spa.core.ui.components.spaTopo
import com.tana.spa.feature.admin.presentation.AdminCodesEvent
import com.tana.spa.feature.admin.presentation.AdminCodesState
import com.tana.spa.feature.admin.presentation.DiscountKind
import com.tana.spa.feature.admin.presentation.EligibleTreatments
import com.tana.spa.feature.admin.presentation.label
import com.tana.spa.feature.admin.resources.Res
import com.tana.spa.feature.admin.resources.admin_create_amount
import com.tana.spa.feature.admin.resources.admin_create_cancel
import com.tana.spa.feature.admin.resources.admin_create_close
import com.tana.spa.feature.admin.resources.admin_create_code
import com.tana.spa.feature.admin.resources.admin_create_code_help
import com.tana.spa.feature.admin.resources.admin_create_date_placeholder
import com.tana.spa.feature.admin.resources.admin_create_duplicate
import com.tana.spa.feature.admin.resources.admin_create_eligible
import com.tana.spa.feature.admin.resources.admin_create_ends
import com.tana.spa.feature.admin.resources.admin_create_limit
import com.tana.spa.feature.admin.resources.admin_create_limit_help
import com.tana.spa.feature.admin.resources.admin_create_minimum
import com.tana.spa.feature.admin.resources.admin_create_minimum_help
import com.tana.spa.feature.admin.resources.admin_create_overlap
import com.tana.spa.feature.admin.resources.admin_create_percent
import com.tana.spa.feature.admin.resources.admin_create_preview
import com.tana.spa.feature.admin.resources.admin_create_preview_limit
import com.tana.spa.feature.admin.resources.admin_create_preview_minimum
import com.tana.spa.feature.admin.resources.admin_create_preview_offer
import com.tana.spa.feature.admin.resources.admin_create_preview_valid
import com.tana.spa.feature.admin.resources.admin_create_publish
import com.tana.spa.feature.admin.resources.admin_create_starts
import com.tana.spa.feature.admin.resources.admin_create_title
import com.tana.spa.feature.admin.resources.admin_create_value
import org.jetbrains.compose.resources.stringResource

/**
 * The create form. The web design slides it in from the right; a dialog is the equivalent that
 * works on a phone as well as a desk screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateCodePanel(state: AdminCodesState, onEvent: (AdminCodesEvent) -> Unit) {
    val draft = state.draft
    ModalBottomSheet(
        onDismissRequest = { onEvent(AdminCodesEvent.CreateDismissed) },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.widthIn(max = 560.dp).align(Alignment.CenterHorizontally)) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.admin_create_title),
                        style = SpaTheme.textStyles.cardTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onEvent(AdminCodesEvent.CreateDismissed) }) {
                        SpaIconImage(
                            icon = SpaIcon.Close,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = stringResource(Res.string.admin_create_close),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpaTextField(
                        label = stringResource(Res.string.admin_create_code),
                        value = draft.code,
                        onValueChange = {
                            onEvent(AdminCodesEvent.DraftChanged(draft.copy(code = it.uppercase().filter { c -> !c.isWhitespace() })))
                        },
                        placeholder = "",
                        helper = stringResource(Res.string.admin_create_code_help),
                        error = if (state.duplicateCode) stringResource(Res.string.admin_create_duplicate) else null,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SingleChoiceSegmentedButtonRow {
                            DiscountKind.entries.forEachIndexed { index, kind ->
                                SegmentedButton(
                                    selected = draft.kind == kind,
                                    onClick = { onEvent(AdminCodesEvent.DraftChanged(draft.copy(kind = kind))) },
                                    shape = SegmentedButtonDefaults.itemShape(index, DiscountKind.entries.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        activeContentColor = MaterialTheme.colorScheme.primary,
                                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                ) {
                                    Text(
                                        text = when (kind) {
                                            DiscountKind.PERCENT -> stringResource(Res.string.admin_create_percent)
                                            DiscountKind.AMOUNT -> stringResource(Res.string.admin_create_amount)
                                        },
                                        style = SpaTheme.textStyles.body,
                                    )
                                }
                            }
                        }
                        SpaTextField(
                            label = stringResource(Res.string.admin_create_value),
                            value = draft.value,
                            onValueChange = { onEvent(AdminCodesEvent.DraftChanged(draft.copy(value = it.filter(Char::isDigit)))) },
                            placeholder = if (draft.kind == DiscountKind.PERCENT) "%" else "$",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(Res.string.admin_create_eligible),
                            style = SpaTheme.textStyles.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EligibleTreatments.entries.forEach { option ->
                                SpaFilterChip(
                                    label = option.label,
                                    selected = draft.eligible == option,
                                    onClick = { onEvent(AdminCodesEvent.DraftChanged(draft.copy(eligible = option))) },
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpaTextField(
                            label = stringResource(Res.string.admin_create_starts),
                            value = draft.starts,
                            onValueChange = { onEvent(AdminCodesEvent.DraftChanged(draft.copy(starts = it))) },
                            placeholder = stringResource(Res.string.admin_create_date_placeholder),
                            modifier = Modifier.weight(1f),
                        )
                        SpaTextField(
                            label = stringResource(Res.string.admin_create_ends),
                            value = draft.ends,
                            onValueChange = { onEvent(AdminCodesEvent.DraftChanged(draft.copy(ends = it))) },
                            placeholder = stringResource(Res.string.admin_create_date_placeholder),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpaTextField(
                            label = stringResource(Res.string.admin_create_limit),
                            value = draft.limit,
                            onValueChange = { onEvent(AdminCodesEvent.DraftChanged(draft.copy(limit = it.filter(Char::isDigit)))) },
                            placeholder = "",
                            helper = stringResource(Res.string.admin_create_limit_help),
                            modifier = Modifier.weight(1f),
                        )
                        SpaTextField(
                            label = stringResource(Res.string.admin_create_minimum),
                            value = draft.minimumSpend,
                            onValueChange = {
                                onEvent(AdminCodesEvent.DraftChanged(draft.copy(minimumSpend = it.filter(Char::isDigit))))
                            },
                            placeholder = "$",
                            helper = stringResource(Res.string.admin_create_minimum_help),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    state.overlappingCode?.let { overlap ->
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = SpaTheme.extraColors.warningContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SpaIconImage(SpaIcon.Warning, size = 18.dp, tint = SpaTheme.extraColors.warning)
                                Text(
                                    text = stringResource(
                                        Res.string.admin_create_overlap,
                                        overlap.code,
                                        overlap.eligible.lowercase(),
                                    ),
                                    style = SpaTheme.textStyles.body,
                                    color = SpaTheme.extraColors.warning,
                                )
                            }
                        }
                    }

                    GuestPreview(state)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpaSecondaryButton(
                        text = stringResource(Res.string.admin_create_cancel),
                        onClick = { onEvent(AdminCodesEvent.CreateDismissed) },
                        modifier = Modifier.weight(1f),
                    )
                    SpaPrimaryButton(
                        text = stringResource(Res.string.admin_create_publish),
                        onClick = { onEvent(AdminCodesEvent.Publish) },
                        enabled = state.canPublish,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** What the offers screen will show once the code goes live. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuestPreview(state: AdminCodesState) {
    val draft = state.draft
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.admin_create_preview),
            style = SpaTheme.textStyles.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .spaTopo(Alignment.TopEnd),
        ) {
            Surface(shape = SpaPebbleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            Res.string.admin_create_preview_offer,
                            draft.offer,
                            draft.eligible.label.lowercase(),
                        ),
                        style = SpaTheme.textStyles.screenTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    val minimum = if (draft.minimumSpend.isBlank()) {
                        ""
                    } else {
                        stringResource(Res.string.admin_create_preview_minimum, draft.minimumSpend)
                    }
                    val valid = stringResource(Res.string.admin_create_preview_valid, draft.starts, draft.ends)
                    val limit = if (draft.limit.isBlank()) {
                        ""
                    } else {
                        stringResource(Res.string.admin_create_preview_limit, draft.limit)
                    }
                    Text(
                        text = listOf(minimum, valid, limit).filter { it.isNotEmpty() }.joinToString(" "),
                        style = SpaTheme.textStyles.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SpaCodeChip(draft.code.ifBlank { "CODE" }, Modifier.widthIn(max = 220.dp))
                }
            }
        }
    }
}
