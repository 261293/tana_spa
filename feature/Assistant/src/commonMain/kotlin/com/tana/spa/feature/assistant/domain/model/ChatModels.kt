package com.tana.spa.feature.assistant.domain.model

enum class Author { ASSISTANT, USER }

/** [text] may hold several paragraphs separated by a blank line; the UI renders them in one bubble. */
data class ChatMessage(val id: Long, val author: Author, val text: String)

/**
 * What the assistant says next and how far through the conversation the user is.
 *
 * @property progress 0f..1f, drives the progress strip; values outside the range are clamped by the UI.
 * @property finished the conversation has reached its end: the input is replaced by a continue button.
 */
data class AssistantReply(val text: String, val progress: Float, val finished: Boolean = false)
