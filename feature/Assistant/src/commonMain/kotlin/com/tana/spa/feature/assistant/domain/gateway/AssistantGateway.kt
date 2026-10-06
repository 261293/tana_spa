package com.tana.spa.feature.assistant.domain.gateway

import com.tana.spa.feature.assistant.domain.model.AssistantReply
import com.tana.spa.feature.assistant.domain.model.ChatMessage

/**
 * The seam where the AI plugs in. Implement this against your model/backend and bind it in
 * [com.tana.spa.feature.assistant.di.assistantModule] in place of the scripted stand-in; the UI,
 * state handling, retry and progress display need no changes.
 *
 * Both calls may suspend for as long as the model takes (the UI shows a typing indicator) and may
 * throw on any failure (offline, timeout, server error): the chat then offers "retry" and keeps the
 * conversation intact. Cancellation must be propagated, not swallowed.
 */
interface AssistantGateway {
    /** The opening message, requested once when the chat opens. */
    suspend fun greet(): AssistantReply

    /**
     * The reply to the conversation so far. [history] is oldest-first and always ends with the
     * user's newest message.
     */
    suspend fun reply(history: List<ChatMessage>): AssistantReply
}
