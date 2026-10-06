package com.tana.spa.feature.assistant.presentation.manipulator

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.assistant.domain.model.AssistantReply
import com.tana.spa.feature.assistant.domain.model.Author
import com.tana.spa.feature.assistant.domain.model.ChatMessage
import com.tana.spa.feature.assistant.domain.usecase.SendChatMessageUseCase
import com.tana.spa.feature.assistant.domain.usecase.StartChatUseCase
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class ChatViewModel(
    private val startChat: StartChatUseCase,
    private val sendMessage: SendChatMessageUseCase,
) : MviViewModel<ChatState, ChatEvent, ChatEffect>(ChatState()) {

    private var nextId = 0L

    // Not derived from state: a second tap must be ignored even before the first one's
    // `isReplying` update has been observed.
    private var requestInFlight = false

    init {
        requestGreeting()
    }

    override fun onEvent(event: ChatEvent) {
        when (event) {
            // Capped: an unbounded paste would be sent to the model verbatim.
            is ChatEvent.InputChanged -> setState { copy(input = event.value.take(MAX_INPUT_LENGTH)) }
            ChatEvent.Send -> send()
            ChatEvent.Retry -> retry()
            ChatEvent.Close -> sendEffect(ChatEffect.NavigateHome)
        }
    }

    private fun send() {
        val current = state.value
        if (!current.canSend || requestInFlight) return
        val text = current.input.trim()
        setState { copy(messages = messages + newMessage(Author.USER, text), input = "") }
        requestReply()
    }

    private fun retry() {
        if (!state.value.failed || requestInFlight) return
        // Nothing yet from the assistant means the greeting itself failed; otherwise the user's
        // last message is waiting for an answer.
        if (state.value.messages.isEmpty()) requestGreeting() else requestReply()
    }

    private fun requestGreeting() = launchRequest { startChat() }

    private fun requestReply() {
        val history = state.value.messages
        launchRequest { sendMessage(history) }
    }

    private fun launchRequest(call: suspend () -> AssistantReply) {
        requestInFlight = true
        setState { copy(isReplying = true, failed = false) }
        viewModelScope.launch {
            val reply = try {
                call()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Any gateway failure (offline, timeout, model error) is retryable; the details are
                // the gateway's to log.
                null
            } finally {
                requestInFlight = false
            }
            if (reply == null) {
                setState { copy(isReplying = false, failed = true) }
            } else {
                setState {
                    copy(
                        messages = messages + newMessage(Author.ASSISTANT, reply.text),
                        isReplying = false,
                        progress = reply.progress.coerceIn(0f, 1f),
                        finished = reply.finished,
                    )
                }
            }
        }
    }

    private fun newMessage(author: Author, text: String) = ChatMessage(nextId++, author, text)

    private companion object {
        const val MAX_INPUT_LENGTH = 1_000
    }
}
