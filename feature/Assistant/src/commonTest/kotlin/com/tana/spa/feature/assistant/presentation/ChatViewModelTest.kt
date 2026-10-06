package com.tana.spa.feature.assistant.presentation

import com.tana.spa.feature.assistant.domain.gateway.AssistantGateway
import com.tana.spa.feature.assistant.domain.model.AssistantReply
import com.tana.spa.feature.assistant.domain.model.Author
import com.tana.spa.feature.assistant.domain.model.ChatMessage
import com.tana.spa.feature.assistant.domain.usecase.SendChatMessageUseCase
import com.tana.spa.feature.assistant.domain.usecase.StartChatUseCase
import com.tana.spa.feature.assistant.presentation.manipulator.ChatEffect
import com.tana.spa.feature.assistant.presentation.manipulator.ChatEvent
import com.tana.spa.feature.assistant.presentation.manipulator.ChatViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    /** Scripted gateway double: queue replies, make calls fail, or hold one open with [gate]. */
    private class FakeGateway : AssistantGateway {
        var greeting: () -> AssistantReply = { AssistantReply("Hello!", 0f) }
        var replies = ArrayDeque<() -> AssistantReply>()
        var gate: CompletableDeferred<Unit>? = null
        val histories = mutableListOf<List<ChatMessage>>()
        var greetCalls = 0

        override suspend fun greet(): AssistantReply {
            greetCalls++
            return greeting()
        }

        override suspend fun reply(history: List<ChatMessage>): AssistantReply {
            histories += history
            gate?.await()
            return (replies.removeFirstOrNull() ?: { AssistantReply("ok", 0.2f) })()
        }
    }

    private val gateway = FakeGateway()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ChatViewModel(StartChatUseCase(gateway), SendChatMessageUseCase(gateway))

    private fun ChatViewModel.say(text: String) {
        onEvent(ChatEvent.InputChanged(text))
        onEvent(ChatEvent.Send)
    }

    @Test
    fun theChatOpensWithTheAssistantsGreeting() {
        val vm = viewModel()

        val s = vm.state.value
        assertEquals(listOf(Author.ASSISTANT to "Hello!"), s.messages.map { it.author to it.text })
        assertFalse(s.isReplying)
        assertFalse(s.failed)
        assertEquals(0f, s.progress)
    }

    @Test
    fun sendingAppendsTheUserMessageThenTheReplyAndUpdatesProgress() {
        gateway.replies += { AssistantReply("Tell me more", 0.4f) }
        val vm = viewModel()
        vm.say("  my skin is dry  ")

        val s = vm.state.value
        assertEquals(
            listOf(Author.ASSISTANT to "Hello!", Author.USER to "my skin is dry", Author.ASSISTANT to "Tell me more"),
            s.messages.map { it.author to it.text },
        )
        assertEquals("", s.input)
        assertEquals(0.4f, s.progress)
        assertFalse(s.isReplying)
        // The gateway gets the whole conversation, ending with the user's newest message.
        assertEquals(listOf("Hello!", "my skin is dry"), gateway.histories.single().map { it.text })
    }

    @Test
    fun messageIdsAreUniqueSoTheListCanKeyOnThem() {
        val vm = viewModel()
        vm.say("one")
        vm.say("two")
        val ids = vm.state.value.messages.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun blankInputCannotBeSent() {
        val vm = viewModel()
        vm.say("   ")
        vm.onEvent(ChatEvent.Send)

        assertTrue(gateway.histories.isEmpty())
        assertEquals(1, vm.state.value.messages.size)
        assertFalse(vm.state.value.canSend)
    }

    @Test
    fun nothingCanBeSentWhileTheAssistantIsStillReplying() {
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel()
        gateway.gate = gate

        vm.say("first")
        assertTrue(vm.state.value.isReplying)

        // The user may keep typing, but Send (button or keyboard) is ignored until the reply lands.
        vm.onEvent(ChatEvent.InputChanged("second"))
        assertFalse(vm.state.value.canSend)
        vm.onEvent(ChatEvent.Send)
        assertEquals(1, gateway.histories.size)
        assertEquals("second", vm.state.value.input, "typed text must not be lost")

        gate.complete(Unit)
        assertFalse(vm.state.value.isReplying)
        assertTrue(vm.state.value.canSend)
    }

    @Test
    fun inputIsCappedBeforeItCanReachTheModel() {
        val vm = viewModel()
        vm.onEvent(ChatEvent.InputChanged("a".repeat(5_000)))
        assertEquals(1_000, vm.state.value.input.length)
    }

    @Test
    fun aFailedGreetingCanBeRetried() {
        var attempts = 0
        gateway.greeting = { if (attempts++ == 0) error("offline") else AssistantReply("Hello again", 0f) }
        val vm = viewModel()

        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.isReplying)
        assertTrue(vm.state.value.messages.isEmpty())

        vm.onEvent(ChatEvent.Retry)
        assertFalse(vm.state.value.failed)
        assertEquals(listOf("Hello again"), vm.state.value.messages.map { it.text })
        assertEquals(2, gateway.greetCalls)
    }

    @Test
    fun aFailedReplyKeepsTheUsersMessageAndRetryDoesNotDuplicateIt() {
        gateway.replies += { error("timeout") }
        gateway.replies += { AssistantReply("Here you go", 0.5f) }
        val vm = viewModel()
        vm.say("help")

        val failed = vm.state.value
        assertTrue(failed.failed)
        assertEquals(listOf("Hello!", "help"), failed.messages.map { it.text }, "the message must survive the failure")
        assertFalse(failed.canSend, "sending is blocked until the failure is resolved")

        vm.onEvent(ChatEvent.Retry)
        val s = vm.state.value
        assertFalse(s.failed)
        assertEquals(listOf("Hello!", "help", "Here you go"), s.messages.map { it.text })
        // Both attempts saw the identical history: the user's message was not re-added.
        assertEquals(gateway.histories[0], gateway.histories[1])
    }

    @Test
    fun retryDoesNothingWhenNothingFailed() {
        val vm = viewModel()
        vm.onEvent(ChatEvent.Retry)
        assertEquals(1, gateway.greetCalls)
        assertTrue(gateway.histories.isEmpty())
    }

    @Test
    fun theFinalReplyEndsTheConversation() {
        gateway.replies += { AssistantReply("Thanks, that's all", 1f, finished = true) }
        val vm = viewModel()
        vm.say("last answer")

        assertTrue(vm.state.value.finished)
        assertEquals(1f, vm.state.value.progress)

        vm.say("one more?")
        assertEquals(1, gateway.histories.size, "no new request after the conversation has finished")
    }

    @Test
    fun progressFromTheModelIsClampedToARange() {
        gateway.replies += { AssistantReply("a", 7f) }
        val vm = viewModel()
        vm.say("x")
        assertEquals(1f, vm.state.value.progress)

        gateway.replies += { AssistantReply("b", -3f) }
        vm.say("y")
        assertEquals(0f, vm.state.value.progress)
    }

    @Test
    fun closeNavigatesHome() = runTest {
        val vm = viewModel()
        vm.onEvent(ChatEvent.Close)
        assertEquals(ChatEffect.NavigateHome, vm.effects.first())
    }
}
