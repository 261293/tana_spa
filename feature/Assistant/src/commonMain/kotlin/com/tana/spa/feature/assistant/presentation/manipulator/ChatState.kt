package com.tana.spa.feature.assistant.presentation.manipulator

import com.tana.spa.feature.assistant.domain.model.ChatMessage

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    /** The assistant is "typing": shown from the moment the chat opens or a message is sent. */
    val isReplying: Boolean = true,
    /** 0f..1f. */
    val progress: Float = 0f,
    /** The conversation reached its end; the input bar gives way to a continue button. */
    val finished: Boolean = false,
    /** The last request failed; the conversation is intact and [ChatEvent.Retry] repeats it. */
    val failed: Boolean = false,
) {
    val canSend: Boolean get() = input.isNotBlank() && !isReplying && !finished && !failed
}

sealed interface ChatEvent {
    data class InputChanged(val value: String) : ChatEvent
    data object Send : ChatEvent
    data object Retry : ChatEvent

    /** Leave the chat for the app's home. */
    data object Close : ChatEvent
}

sealed interface ChatEffect {
    data object NavigateHome : ChatEffect
}
