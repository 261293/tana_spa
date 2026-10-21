package com.tana.spa.feature.assistant.presentation.ui

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.feature.assistant.domain.model.Author
import com.tana.spa.feature.assistant.domain.model.ChatMessage
import com.tana.spa.feature.assistant.presentation.manipulator.ChatEffect
import com.tana.spa.feature.assistant.presentation.manipulator.ChatEvent
import com.tana.spa.feature.assistant.presentation.manipulator.ChatState
import com.tana.spa.feature.assistant.resources.Res
import com.tana.spa.feature.assistant.resources.assistant_name
import com.tana.spa.feature.assistant.resources.assistant_subtitle
import com.tana.spa.feature.assistant.resources.chat_assistant_said
import com.tana.spa.feature.assistant.resources.chat_close
import com.tana.spa.feature.assistant.resources.chat_failed
import com.tana.spa.feature.assistant.resources.chat_finished_cta
import com.tana.spa.feature.assistant.resources.chat_input_label
import com.tana.spa.feature.assistant.resources.chat_input_placeholder
import com.tana.spa.feature.assistant.resources.chat_progress_description
import com.tana.spa.feature.assistant.resources.chat_progress_label
import com.tana.spa.feature.assistant.resources.chat_retry
import com.tana.spa.feature.assistant.resources.chat_send
import com.tana.spa.feature.assistant.resources.chat_typing
import com.tana.spa.feature.assistant.resources.chat_you_said
import com.tana.spa.feature.assistant.resources.ic_close
import com.tana.spa.feature.assistant.resources.ic_send
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Assistant chat. Pure UI over [ChatState]: it knows nothing about where answers come from, so
 * connecting the real AI means implementing `AssistantGateway`, with no change here.
 */
@Composable
fun ChatScreen(state: ChatState, effects: Flow<ChatEffect>, onEvent: (ChatEvent) -> Unit, onNavigateHome: () -> Unit) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                ChatEffect.NavigateHome -> onNavigateHome()
            }
        }
    }

    ScreenCard(maxWidth = 720.dp, cardColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            ChatHeader(onClose = { onEvent(ChatEvent.Close) })
            ProgressStrip(progress = state.progress)
            MessageList(state = state, onRetry = { onEvent(ChatEvent.Retry) }, modifier = Modifier.weight(1f))
            if (state.finished) {
                SpaPrimaryButton(
                    text = stringResource(Res.string.chat_finished_cta),
                    onClick = { onEvent(ChatEvent.Close) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            } else {
                InputBar(
                    value = state.input,
                    onValueChange = { onEvent(ChatEvent.InputChanged(it)) },
                    onSend = { onEvent(ChatEvent.Send) },
                    canSend = state.canSend,
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantAvatar(size = 44.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = stringResource(Res.string.assistant_name),
                style = SpaTheme.textStyles.prompt,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(SpaTheme.extraColors.success, CircleShape))
                Text(
                    text = stringResource(Res.string.assistant_subtitle),
                    style = SpaTheme.textStyles.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        IconSquareButton(
            icon = Res.drawable.ic_close,
            description = stringResource(Res.string.chat_close),
            onClick = onClose,
            round = true,
        )
    }
}

@Composable
private fun ProgressStrip(progress: Float) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(SpaTheme.motion.long))
    val percent = (animated * 100).toInt()
    val description = pluralStringResource(Res.plurals.chat_progress_description, percent, percent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(animated, 0f..1f)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.chat_progress_label),
                style = SpaTheme.textStyles.overline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animated)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Text(
            text = "$percent%",
            style = SpaTheme.textStyles.label,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .padding(start = 12.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun MessageList(state: ChatState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // Keep the newest thing (message, typing dots or the retry row) in view.
    val extraRows = (if (state.isReplying) 1 else 0) + (if (state.failed) 1 else 0)
    val lastIndex = state.messages.size + extraRows - 1
    LaunchedEffect(lastIndex) {
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(state.messages, key = { it.id }) { message -> MessageRow(message) }
        if (state.isReplying) item(key = "typing") { TypingRow() }
        if (state.failed) item(key = "failed") { FailedRow(onRetry) }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    when (message.author) {
        Author.ASSISTANT -> AssistantRow(message)
        Author.USER -> UserRow(message)
    }
}

@Composable
private fun AssistantRow(message: ChatMessage) {
    val name = stringResource(Res.string.assistant_name)
    val spoken = stringResource(Res.string.chat_assistant_said, name, message.text.replace("\n\n", ". "))
    Row(verticalAlignment = Alignment.Bottom) {
        AssistantAvatar(size = 32.dp)
        Spacer(Modifier.width(8.dp))
        BoxWithConstraints(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth * 0.86f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // One bubble, one paragraph per blank-line-separated chunk, as in the reference.
                message.text.split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
                    Text(text = paragraph, style = SpaTheme.textStyles.bodyRelaxed, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun UserRow(message: ChatMessage) {
    val spoken = stringResource(Res.string.chat_you_said, message.text)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        BoxWithConstraints {
            Text(
                text = message.text,
                style = SpaTheme.textStyles.bodyRelaxed,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .widthIn(max = maxWidth * 0.8f)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics { contentDescription = spoken },
            )
        }
    }
}

@Composable
private fun TypingRow() {
    val description = stringResource(Res.string.chat_typing)
    Row(verticalAlignment = Alignment.Bottom) {
        AssistantAvatar(size = 32.dp)
        Spacer(Modifier.width(8.dp))
        val transition = rememberInfiniteTransition(label = "typing")
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp)
                // Announced politely to screen readers when it appears.
                .semantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val motion = SpaTheme.motion
            repeat(3) { index ->
                // Under reduced motion the dots stand still: a loop has no end state to snap to.
                val alpha = if (motion.reduced) 1f else typingDotAlpha(transition, index, motion.long)
                Box(Modifier.size(7.dp).alpha(alpha).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            }
        }
    }
}

/** The dots fade in turn rather than together, which is what reads as typing. */
@Composable
private fun typingDotAlpha(transition: InfiniteTransition, index: Int, durationMs: Int): Float {
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMs),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(index * DOT_STAGGER_MS),
        ),
        label = "dot$index",
    )
    return alpha
}

private const val DOT_STAGGER_MS = 160

@Composable
private fun FailedRow(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.chat_failed),
            style = SpaTheme.textStyles.caption,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(Res.string.chat_retry),
            style = SpaTheme.textStyles.body,
            fontWeight = FontWeight.Bold,
            color = SpaTheme.extraColors.link,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun InputBar(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, canSend: Boolean) {
    // BasicTextField does not inherit the theme font the way Text does, so the token carries it.
    val textStyle = SpaTheme.textStyles.chatInput.copy(color = MaterialTheme.colorScheme.onSurface)
    val label = stringResource(Res.string.chat_input_label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.chat_input_placeholder),
                            style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                    inner()
                }
            },
        )
        val sendDescription = stringResource(Res.string.chat_send)
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                .clickable(enabled = canSend, role = Role.Button, onClick = onSend)
                .semantics { contentDescription = sendDescription },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_send),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
