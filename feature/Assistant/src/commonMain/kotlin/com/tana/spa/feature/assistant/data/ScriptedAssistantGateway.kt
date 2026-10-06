package com.tana.spa.feature.assistant.data

import com.tana.spa.feature.assistant.domain.gateway.AssistantGateway
import com.tana.spa.feature.assistant.domain.model.AssistantReply
import com.tana.spa.feature.assistant.domain.model.Author
import com.tana.spa.feature.assistant.domain.model.ChatMessage
import kotlinx.coroutines.delay

/**
 * STAND-IN, not an AI: walks through a fixed list of questions so the chat UI can be run and
 * demonstrated end to end. Replace the binding in `assistantModule` with the real gateway.
 *
 * The wording is Vietnamese placeholder copy; the real assistant will write its own.
 */
class ScriptedAssistantGateway(
    /** Simulated model latency, so the typing indicator is visible. */
    private val thinkingMillis: Long = 900,
) : AssistantGateway {

    override suspend fun greet(): AssistantReply {
        delay(thinkingMillis)
        return AssistantReply(text = GREETING, progress = 0f)
    }

    override suspend fun reply(history: List<ChatMessage>): AssistantReply {
        delay(thinkingMillis)
        val answered = history.count { it.author == Author.USER }
        val next = FOLLOW_UPS.getOrNull(answered - 1)
        return if (next != null) {
            AssistantReply(text = next, progress = answered.toFloat() / TOTAL_STEPS)
        } else {
            AssistantReply(text = CLOSING, progress = 1f, finished = true)
        }
    }

    private companion object {
        val FOLLOW_UPS = listOf(
            "Cảm ơn bạn đã chia sẻ.\n\nBạn đang quan tâm nhất đến làn da hay chăm sóc cơ thể?",
            "Mình hiểu rồi.\n\nBạn đã từng dùng dịch vụ spa hay liệu trình nào chưa?",
            "Tuyệt vời.\n\nBạn thường rảnh vào khung giờ nào trong tuần để đặt lịch?",
            "Câu hỏi cuối cùng:\n\nBạn có lưu ý hay dị ứng nào mình nên biết không?",
        )
        val TOTAL_STEPS = FOLLOW_UPS.size + 1

        const val GREETING =
            "Xin chào, mình là Tana, trợ lý của Tana Spa.\n\n" +
                "Rất vui vì bạn ở đây. Mình sẽ cùng bạn đi qua vài câu hỏi ngắn để gợi ý dịch vụ phù hợp nhất.\n\n" +
                "Gần đây bạn đang băn khoăn điều gì về làn da hoặc sức khỏe của mình?"

        const val CLOSING =
            "Cảm ơn bạn, mình đã có đủ thông tin.\n\nGiờ bạn có thể khám phá các dịch vụ phù hợp với mình."
    }
}
