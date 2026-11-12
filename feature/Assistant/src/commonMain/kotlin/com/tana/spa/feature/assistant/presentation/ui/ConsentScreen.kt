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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.revealOnScroll
import com.tana.spa.feature.assistant.resources.Res
import com.tana.spa.feature.assistant.resources.back
import com.tana.spa.feature.assistant.resources.consent_agree
import com.tana.spa.feature.assistant.resources.consent_body
import com.tana.spa.feature.assistant.resources.consent_button
import com.tana.spa.feature.assistant.resources.consent_title
import com.tana.spa.feature.assistant.resources.ic_arrow_back
import com.tana.spa.feature.assistant.resources.welcome_privacy
import com.tana.spa.feature.assistant.resources.welcome_terms
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
                    style = SpaTheme.textStyles.screenTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.revealOnScroll().semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.consent_body),
                    style = SpaTheme.textStyles.bodyLoose,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.revealOnScroll(order = 1).padding(top = 16.dp),
                )
            }

            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ConsentCheckRow(checked = agreed, onCheckedChange = onAgreedChange) {
                        Text(text = acceptText(), style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurface)
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

/**
 * The one consent sentence, with the two document names styled
 * as links. They are not tappable yet: there are no published documents to open. The words are
 * found inside the translated sentence, so the styling survives any word order.
 */
@Composable
private fun acceptText(): AnnotatedString {
    val terms = stringResource(Res.string.welcome_terms)
    val privacy = stringResource(Res.string.welcome_privacy)
    val sentence = stringResource(Res.string.consent_agree, terms, privacy)
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
