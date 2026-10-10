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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.feature.assistant.resources.Res
import com.tana.spa.feature.assistant.resources.welcome_accept
import com.tana.spa.feature.assistant.resources.welcome_body
import com.tana.spa.feature.assistant.resources.welcome_brand
import com.tana.spa.feature.assistant.resources.welcome_bubble
import com.tana.spa.feature.assistant.resources.welcome_button
import com.tana.spa.feature.assistant.resources.welcome_greeting
import com.tana.spa.feature.assistant.resources.welcome_privacy
import com.tana.spa.feature.assistant.resources.welcome_terms
import org.jetbrains.compose.resources.stringResource

/**
 * Welcome: the assistant introduces itself and the user accepts the terms before continuing.
 * Stateless; the checkbox state lives with the caller.
 */
@Composable
fun WelcomeScreen(accepted: Boolean, onAcceptedChange: (Boolean) -> Unit, onContinue: () -> Unit) {
    ScreenCard(maxWidth = 520.dp) {
        Column(Modifier.fillMaxSize()) {
            // Hero: soft pink gradient with the assistant and its greeting bubble.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Brush.verticalGradient(SpaTheme.extraColors.heroGradient)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.welcome_bubble),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    AssistantAvatar(size = 112.dp, modifier = Modifier.padding(top = 14.dp))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.welcome_greeting),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.welcome_brand),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.welcome_body),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ConsentCheckRow(checked = accepted, onCheckedChange = onAcceptedChange) {
                    Text(text = acceptText(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                SpaPrimaryButton(
                    text = stringResource(Res.string.welcome_button),
                    onClick = onContinue,
                    enabled = accepted,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

/**
 * "I accept the Terms & Conditions and the Privacy Policy." with the two document names styled
 * as links. They are not tappable yet: there are no published documents to open. The words are
 * found inside the translated sentence, so the styling survives any word order.
 */
@Composable
private fun acceptText(): AnnotatedString {
    val terms = stringResource(Res.string.welcome_terms)
    val privacy = stringResource(Res.string.welcome_privacy)
    val sentence = stringResource(Res.string.welcome_accept, terms, privacy)
    return buildAnnotatedString {
        append(sentence)
        listOf(terms, privacy).forEach { name ->
            val start = sentence.indexOf(name)
            if (start >= 0) {
                addStyle(
                    SpanStyle(
                        color = SpaTheme.extraColors.link,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                    ),
                    start,
                    start + name.length,
                )
            }
        }
    }
}
