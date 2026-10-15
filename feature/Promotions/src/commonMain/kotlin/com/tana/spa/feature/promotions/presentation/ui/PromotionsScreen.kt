package com.tana.spa.feature.promotions.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaPageWidth
import com.tana.spa.core.ui.SpaPebbleShape
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.SpaWindow
import com.tana.spa.core.ui.components.SpaCard
import com.tana.spa.core.ui.components.SpaCodeChip
import com.tana.spa.core.ui.components.SpaIcon
import com.tana.spa.core.ui.components.SpaIconImage
import com.tana.spa.core.ui.components.SpaImageTile
import com.tana.spa.core.ui.components.SpaPage
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaScreenHeader
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.core.ui.components.SpaTextField
import com.tana.spa.core.ui.components.spaFelt
import com.tana.spa.core.ui.components.spaTopo
import com.tana.spa.feature.promotions.presentation.PromotionsEffect
import com.tana.spa.feature.promotions.presentation.PromotionsEvent
import com.tana.spa.feature.promotions.presentation.PromotionsState
import com.tana.spa.feature.promotions.resources.Res
import com.tana.spa.feature.promotions.resources.promotions_book_any
import com.tana.spa.feature.promotions.resources.promotions_book_eligible
import com.tana.spa.feature.promotions.resources.promotions_check
import com.tana.spa.feature.promotions.resources.promotions_check_failed
import com.tana.spa.feature.promotions.resources.promotions_check_ok
import com.tana.spa.feature.promotions.resources.promotions_code_label
import com.tana.spa.feature.promotions.resources.promotions_code_placeholder
import com.tana.spa.feature.promotions.resources.promotions_copied
import com.tana.spa.feature.promotions.resources.promotions_copy
import com.tana.spa.feature.promotions.resources.promotions_have_code_body
import com.tana.spa.feature.promotions.resources.promotions_have_code_title
import com.tana.spa.feature.promotions.resources.promotions_more
import com.tana.spa.feature.promotions.resources.promotions_subtitle
import com.tana.spa.feature.promotions.resources.promotions_terms_until
import com.tana.spa.feature.promotions.resources.promotions_title
import com.tana.spa.feature.promotions.resources.promotions_title_accent
import com.tana.spa.feature.promotions.resources.promotions_use_code
import com.tana.spa.feature.promotions.resources.promotions_valid_until
import com.tana.spa.shared.spa.Promo
import com.tana.spa.shared.spa.eligibleLabel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Stateless: renders [state], reports intents through [onEvent], routes one-shot [effects]. */
@Composable
fun PromotionsScreen(
    state: PromotionsState,
    effects: Flow<PromotionsEffect>,
    onEvent: (PromotionsEvent) -> Unit,
    onOpenBooking: (String) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is PromotionsEffect.ShowCopied -> {
                    clipboard.setText(AnnotatedString(effect.code))
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(getString(Res.string.promotions_copied, effect.code))
                }

                is PromotionsEffect.OpenBooking -> onOpenBooking(effect.code)
            }
        }
    }

    val wide = SpaWindow.size.isWide
    Box(Modifier.fillMaxSize()) {
        SpaPage(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            maxWidth = SpaPageWidth.Wide,
        ) {
            SpaScreenHeader(
                title = stringResource(Res.string.promotions_title),
                accent = stringResource(Res.string.promotions_title_accent),
                subtitle = stringResource(Res.string.promotions_subtitle),
            )

            FeaturedOffer(state.featured, wide, onEvent)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.promotions_more),
                    style = SpaTheme.textStyles.cardTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                state.others.forEach { promo -> PromoRow(promo, onEvent) }
            }

            CodeChecker(state, onEvent, Modifier.widthIn(max = 560.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FeaturedOffer(promo: Promo, wide: Boolean, onEvent: (PromotionsEvent) -> Unit) {
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = promo.title,
                style = SpaTheme.textStyles.displayTitle,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(promo.terms, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SpaIconImage(SpaIcon.Calendar, size = 16.dp, tint = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = stringResource(Res.string.promotions_valid_until, promo.validUntil),
                    style = SpaTheme.textStyles.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            CopyableCode(promo.code, onEvent)
            SpaPrimaryButton(
                text = if (promo.eligible.isEmpty()) {
                    stringResource(Res.string.promotions_book_any, promo.code)
                } else {
                    stringResource(Res.string.promotions_book_eligible, promo.eligibleLabel(), promo.code)
                },
                onClick = { onEvent(PromotionsEvent.UseCode(promo.code)) },
            )
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, SpaPebbleShape)
            .spaTopo(Alignment.TopEnd),
    ) {
        if (wide) {
            Row(Modifier.heightIn(min = 300.dp)) {
                SpaImageTile(Modifier.weight(1.1f).fillMaxSize(), shape = SpaPebbleShape)
                Box(Modifier.weight(1f)) { body() }
            }
        } else {
            Column {
                SpaImageTile(Modifier.fillMaxWidth().height(180.dp), shape = SpaPebbleShape)
                body()
            }
        }
    }
}

@Composable
private fun CopyableCode(code: String, onEvent: (PromotionsEvent) -> Unit) {
    SpaCodeChip(
        code = code,
        modifier = Modifier.fillMaxWidth(),
        trailing = {
            IconButton(onClick = { onEvent(PromotionsEvent.CodeCopied(code)) }) {
                SpaIconImage(
                    icon = SpaIcon.Copy,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = stringResource(Res.string.promotions_copy, code),
                )
            }
        },
    )
}

@Composable
private fun PromoRow(promo: Promo, onEvent: (PromotionsEvent) -> Unit) {
    SpaCard(Modifier.fillMaxWidth().spaTopo(Alignment.CenterEnd), padding = PaddingValues(0.dp)) {
        ListItem(
            overlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = promo.code,
                        style = SpaTheme.textStyles.body.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(onClick = { onEvent(PromotionsEvent.CodeCopied(promo.code)) }) {
                        SpaIconImage(
                            icon = SpaIcon.Copy,
                            size = 16.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = stringResource(Res.string.promotions_copy, promo.code),
                        )
                    }
                }
            },
            headlineContent = {
                Text(promo.title, style = SpaTheme.textStyles.cardTitle, color = MaterialTheme.colorScheme.onBackground)
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(Res.string.promotions_terms_until, promo.terms, promo.validUntil),
                        style = SpaTheme.textStyles.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SpaSecondaryButton(
                        text = stringResource(Res.string.promotions_use_code),
                        onClick = { onEvent(PromotionsEvent.UseCode(promo.code)) },
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun CodeChecker(state: PromotionsState, onEvent: (PromotionsEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().spaFelt().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.promotions_have_code_title),
            style = SpaTheme.textStyles.cardTitle,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.promotions_have_code_body),
            style = SpaTheme.textStyles.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SpaTextField(
            label = stringResource(Res.string.promotions_code_label),
            value = state.entry,
            onValueChange = { onEvent(PromotionsEvent.EntryChanged(it)) },
            placeholder = stringResource(Res.string.promotions_code_placeholder),
        )
        SpaPrimaryButton(
            text = stringResource(Res.string.promotions_check),
            onClick = { onEvent(PromotionsEvent.CheckEntry) },
            enabled = state.entry.isNotBlank(),
        )
        val checked = state.checked
        if (checked != null || state.checkFailed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpaIconImage(
                    icon = if (checked != null) SpaIcon.CheckCircle else SpaIcon.Warning,
                    size = 18.dp,
                    tint = if (checked != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = if (checked != null) {
                        stringResource(Res.string.promotions_check_ok, checked.code, checked.title, checked.terms)
                    } else {
                        stringResource(Res.string.promotions_check_failed)
                    },
                    style = SpaTheme.textStyles.body,
                    color = if (checked != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
