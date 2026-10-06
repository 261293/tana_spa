package com.tana.spa.feature.assistant.data

import com.tana.spa.feature.assistant.domain.model.AssistantReply
import com.tana.spa.feature.assistant.domain.model.Author
import com.tana.spa.feature.assistant.domain.model.ChatMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// currentTime is how the test observes virtual time passing; the API is still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class ScriptedAssistantGatewayTest {

    private val gateway = ScriptedAssistantGateway(thinkingMillis = 500)

    @Test
    fun greetsAfterTheSimulatedThinkingTime() = runTest {
        val greeting = gateway.greet()

        assertEquals(500, currentTime, "the typing indicator needs time to be visible")
        assertEquals(0f, greeting.progress)
        assertFalse(greeting.finished)
        assertTrue(greeting.text.isNotBlank())
    }

    @Test
    fun walksThroughEveryQuestionWithRisingProgressThenFinishes() = runTest {
        val history = mutableListOf(ChatMessage(0, Author.ASSISTANT, gateway.greet().text))
        val replies = mutableListOf<AssistantReply>()

        // Keep answering until the script says it is done (bounded, in case it never ends).
        repeat(20) {
            if (replies.lastOrNull()?.finished == true) return@repeat
            history += ChatMessage(history.size.toLong(), Author.USER, "answer ${history.size}")
            val reply = gateway.reply(history)
            replies += reply
            history += ChatMessage(history.size.toLong(), Author.ASSISTANT, reply.text)
        }

        val last = replies.last()
        assertTrue(last.finished, "the script must reach its end")
        assertEquals(1f, last.progress)
        assertEquals(5, replies.size)
        val progress = replies.map { it.progress }
        assertEquals(progress.sorted(), progress, "progress never goes backwards")
        assertTrue(replies.dropLast(1).none { it.finished }, "only the last reply finishes the conversation")
    }
}
