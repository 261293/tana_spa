package com.tana.spa.feature.assistant.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.revealOnScroll
import com.tana.spa.feature.assistant.resources.Res
import com.tana.spa.feature.assistant.resources.chat_close
import com.tana.spa.feature.assistant.resources.ic_close
import com.tana.spa.feature.assistant.resources.welcome_body
import com.tana.spa.feature.assistant.resources.welcome_brand
import com.tana.spa.feature.assistant.resources.welcome_bubble
import com.tana.spa.feature.assistant.resources.welcome_button
import com.tana.spa.feature.assistant.resources.welcome_greeting
import org.jetbrains.compose.resources.stringResource

/**
 * Welcome: the assistant introduces itself. Consent is asked once, on the next screen.
 */
@Composable
fun WelcomeScreen(onContinue: () -> Unit, onClose: () -> Unit) {
    ScreenCard(maxWidth = 520.dp) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.welcome_bubble),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = SpaTheme.textStyles.body,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    AssistantAvatar(size = 112.dp, modifier = Modifier.padding(top = 14.dp))
                }
                IconSquareButton(
                    icon = Res.drawable.ic_close,
                    description = stringResource(Res.string.chat_close),
                    onClick = onClose,
                    round = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.welcome_greeting),
                    style = SpaTheme.textStyles.sectionLead,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.revealOnScroll().semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.welcome_brand),
                    style = SpaTheme.textStyles.brandTitle,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.revealOnScroll(order = 1),
                )
                Text(
                    text = stringResource(Res.string.welcome_body),
                    style = SpaTheme.textStyles.sectionLead,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.revealOnScroll(order = 2).padding(top = 8.dp),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpaPrimaryButton(text = stringResource(Res.string.welcome_button), onClick = onContinue)
            }
        }
    }
}
