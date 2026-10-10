package com.tana.spa.feature.assistant.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.feature.assistant.resources.Res
import com.tana.spa.feature.assistant.resources.back
import com.tana.spa.feature.assistant.resources.consent_agree
import com.tana.spa.feature.assistant.resources.consent_body
import com.tana.spa.feature.assistant.resources.consent_button
import com.tana.spa.feature.assistant.resources.consent_title
import com.tana.spa.feature.assistant.resources.ic_arrow_back
import org.jetbrains.compose.resources.stringResource

/**
 * Data consent: explains how the assistant uses what the user shares, and records their consent.
 * The body text in the string resources is a DRAFT to be replaced with legally approved wording.
 */
@Composable
fun ConsentScreen(agreed: Boolean, onAgreedChange: (Boolean) -> Unit, onBack: () -> Unit, onContinue: () -> Unit) {
    ScreenCard(maxWidth = 520.dp) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            IconSquareButton(
                icon = Res.drawable.ic_arrow_back,
                description = stringResource(Res.string.back),
                onClick = onBack,
                mirrorInRtl = true,
                modifier = Modifier.padding(top = 20.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 28.dp),
            ) {
                Text(
                    text = stringResource(Res.string.consent_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.consent_body),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ConsentCheckRow(checked = agreed, onCheckedChange = onAgreedChange) {
                        Text(
                            text = stringResource(Res.string.consent_agree),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    SpaPrimaryButton(
                        text = stringResource(Res.string.consent_button),
                        onClick = onContinue,
                        enabled = agreed,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
        }
    }
}
