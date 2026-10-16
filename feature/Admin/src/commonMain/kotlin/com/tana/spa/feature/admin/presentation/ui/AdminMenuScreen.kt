package com.tana.spa.feature.admin.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaFilterChip
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.core.ui.components.SpaTextField
import com.tana.spa.feature.admin.presentation.AdminMenuEffect
import com.tana.spa.feature.admin.presentation.AdminMenuEvent
import com.tana.spa.feature.admin.presentation.AdminMenuState
import com.tana.spa.feature.admin.resources.Res
import com.tana.spa.feature.admin.resources.admin_menu_add_service
import com.tana.spa.feature.admin.resources.admin_menu_add_therapist
import com.tana.spa.feature.admin.resources.admin_menu_category
import com.tana.spa.feature.admin.resources.admin_menu_description
import com.tana.spa.feature.admin.resources.admin_menu_description_hint
import com.tana.spa.feature.admin.resources.admin_menu_error_duration_long
import com.tana.spa.feature.admin.resources.admin_menu_error_duration_step
import com.tana.spa.feature.admin.resources.admin_menu_error_duration_zero
import com.tana.spa.feature.admin.resources.admin_menu_error_empty
import com.tana.spa.feature.admin.resources.admin_menu_error_long
import com.tana.spa.feature.admin.resources.admin_menu_error_price_large
import com.tana.spa.feature.admin.resources.admin_menu_error_price_negative
import com.tana.spa.feature.admin.resources.admin_menu_focus
import com.tana.spa.feature.admin.resources.admin_menu_focus_hint
import com.tana.spa.feature.admin.resources.admin_menu_minutes
import com.tana.spa.feature.admin.resources.admin_menu_minutes_hint
import com.tana.spa.feature.admin.resources.admin_menu_name
import com.tana.spa.feature.admin.resources.admin_menu_name_hint
import com.tana.spa.feature.admin.resources.admin_menu_price
import com.tana.spa.feature.admin.resources.admin_menu_price_hint
import com.tana.spa.feature.admin.resources.admin_menu_service_added
import com.tana.spa.feature.admin.resources.admin_menu_service_title
import com.tana.spa.feature.admin.resources.admin_menu_subtitle
import com.tana.spa.feature.admin.resources.admin_menu_therapist_added
import com.tana.spa.feature.admin.resources.admin_menu_therapist_hint
import com.tana.spa.feature.admin.resources.admin_menu_therapist_name
import com.tana.spa.feature.admin.resources.admin_menu_therapist_title
import com.tana.spa.feature.admin.resources.admin_menu_title
import com.tana.spa.feature.admin.resources.admin_menu_title_accent
import com.tana.spa.feature.admin.resources.admin_refused
import com.tana.spa.shared.booking.DurationError
import com.tana.spa.shared.booking.PriceError
import com.tana.spa.shared.booking.ServiceCategoryDto
import com.tana.spa.shared.booking.TextFieldError
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Stateless: renders [state], reports intents through [onEvent], routes one-shot [effects]. */
@Composable
fun AdminMenuScreen(
    state: AdminMenuState,
    effects: Flow<AdminMenuEffect>,
    onEvent: (AdminMenuEvent) -> Unit,
    pendingRequests: Int,
    onOpenRequests: () -> Unit,
    onOpenCodes: () -> Unit,
    onOpenGuestApp: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            val message = when (effect) {
                is AdminMenuEffect.ShowServiceAdded -> getString(Res.string.admin_menu_service_added, effect.name)
                is AdminMenuEffect.ShowTherapistAdded -> getString(Res.string.admin_menu_therapist_added, effect.name)
                is AdminMenuEffect.ShowRefused -> getString(Res.string.admin_refused)
            }
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    AdminScaffold(
        section = AdminSection.MENU,
        pendingCount = pendingRequests,
        onSelect = {
            when (it) {
                AdminSection.REQUESTS -> onOpenRequests()
                AdminSection.CODES -> onOpenCodes()
                AdminSection.MENU -> Unit
            }
        },
        onOpenGuestApp = onOpenGuestApp,
    ) { wide ->
        Box(Modifier.fillMaxSize()) {
            SpaPage(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                maxWidth = SpaPageWidth.Wide,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SpaScreenHeader(
                    title = stringResource(Res.string.admin_menu_title),
                    accent = stringResource(Res.string.admin_menu_title_accent),
                    subtitle = stringResource(Res.string.admin_menu_subtitle),
                )
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(Modifier.weight(1f)) { ServiceForm(state, onEvent) }
                        Box(Modifier.weight(1f)) { TherapistForm(state, onEvent) }
                    }
                } else {
                    ServiceForm(state, onEvent)
                    TherapistForm(state, onEvent)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ServiceForm(state: AdminMenuState, onEvent: (AdminMenuEvent) -> Unit) {
    val form = state.service
    SpaCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTitle(stringResource(Res.string.admin_menu_service_title))
            SpaTextField(
                label = stringResource(Res.string.admin_menu_name),
                value = form.name,
                onValueChange = { onEvent(AdminMenuEvent.ServiceNameChanged(it)) },
                placeholder = stringResource(Res.string.admin_menu_name_hint),
                error = form.nameError?.let { stringResource(textError(it)) },
                enabled = !state.savingService,
            )
            SpaTextField(
                label = stringResource(Res.string.admin_menu_description),
                value = form.description,
                onValueChange = { onEvent(AdminMenuEvent.ServiceDescriptionChanged(it)) },
                placeholder = stringResource(Res.string.admin_menu_description_hint),
                error = form.descriptionError?.let { stringResource(textError(it)) },
                enabled = !state.savingService,
                singleLine = false,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpaTextField(
                    label = stringResource(Res.string.admin_menu_minutes),
                    value = form.minutes,
                    onValueChange = { onEvent(AdminMenuEvent.ServiceMinutesChanged(it)) },
                    placeholder = stringResource(Res.string.admin_menu_minutes_hint),
                    error = form.minutesError?.let { stringResource(durationError(it)) },
                    enabled = !state.savingService,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                SpaTextField(
                    label = stringResource(Res.string.admin_menu_price),
                    value = form.price,
                    onValueChange = { onEvent(AdminMenuEvent.ServicePriceChanged(it)) },
                    placeholder = stringResource(Res.string.admin_menu_price_hint),
                    error = form.priceError?.let { stringResource(priceError(it)) },
                    enabled = !state.savingService,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(Res.string.admin_menu_category),
                style = SpaTheme.textStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceCategoryDto.entries.forEach { category ->
                    SpaFilterChip(
                        label = category.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = category == form.category,
                        onClick = { onEvent(AdminMenuEvent.ServiceCategoryChanged(category)) },
                    )
                }
            }
            SpaPrimaryButton(
                text = stringResource(Res.string.admin_menu_add_service),
                onClick = { onEvent(AdminMenuEvent.SaveService) },
                loading = state.savingService,
            )
        }
    }
}

@Composable
private fun TherapistForm(state: AdminMenuState, onEvent: (AdminMenuEvent) -> Unit) {
    val form = state.therapist
    SpaCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTitle(stringResource(Res.string.admin_menu_therapist_title))
            SpaTextField(
                label = stringResource(Res.string.admin_menu_therapist_name),
                value = form.name,
                onValueChange = { onEvent(AdminMenuEvent.TherapistNameChanged(it)) },
                placeholder = stringResource(Res.string.admin_menu_therapist_hint),
                error = form.nameError?.let { stringResource(textError(it)) },
                enabled = !state.savingTherapist,
            )
            SpaTextField(
                label = stringResource(Res.string.admin_menu_focus),
                value = form.focus,
                onValueChange = { onEvent(AdminMenuEvent.TherapistFocusChanged(it)) },
                placeholder = stringResource(Res.string.admin_menu_focus_hint),
                error = form.focusError?.let { stringResource(textError(it)) },
                enabled = !state.savingTherapist,
            )
            SpaPrimaryButton(
                text = stringResource(Res.string.admin_menu_add_therapist),
                onClick = { onEvent(AdminMenuEvent.SaveTherapist) },
                loading = state.savingTherapist,
            )
        }
    }
}

@Composable
private fun FormTitle(text: String) {
    Text(text = text, style = SpaTheme.textStyles.cardTitle, color = MaterialTheme.colorScheme.onBackground)
}

private fun textError(error: TextFieldError) = when (error) {
    TextFieldError.EMPTY -> Res.string.admin_menu_error_empty
    TextFieldError.TOO_LONG -> Res.string.admin_menu_error_long
}

private fun durationError(error: DurationError) = when (error) {
    DurationError.NOT_POSITIVE -> Res.string.admin_menu_error_duration_zero
    DurationError.TOO_LONG -> Res.string.admin_menu_error_duration_long
    DurationError.NOT_A_MULTIPLE -> Res.string.admin_menu_error_duration_step
}

private fun priceError(error: PriceError) = when (error) {
    PriceError.NEGATIVE -> Res.string.admin_menu_error_price_negative
    PriceError.TOO_LARGE -> Res.string.admin_menu_error_price_large
}
