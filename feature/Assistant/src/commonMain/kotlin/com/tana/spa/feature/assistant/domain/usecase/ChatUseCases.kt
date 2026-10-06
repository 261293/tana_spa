package com.tana.spa.feature.assistant.domain.usecase

import com.tana.spa.feature.assistant.domain.gateway.AssistantGateway
import com.tana.spa.feature.assistant.domain.model.AssistantReply
import com.tana.spa.feature.assistant.domain.model.ChatMessage

class StartChatUseCase(private val gateway: AssistantGateway) {
    suspend operator fun invoke(): AssistantReply = gateway.greet()
}

class SendChatMessageUseCase(private val gateway: AssistantGateway) {
    suspend operator fun invoke(history: List<ChatMessage>): AssistantReply = gateway.reply(history)
}
