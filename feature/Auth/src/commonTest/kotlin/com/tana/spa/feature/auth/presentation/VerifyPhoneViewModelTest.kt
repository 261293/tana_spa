package com.tana.spa.feature.auth.presentation

import com.tana.spa.feature.auth.FakeAuthRepository
import com.tana.spa.feature.auth.TestFixtures
import com.tana.spa.feature.auth.domain.model.OtpFailure
import com.tana.spa.feature.auth.domain.model.OtpSendResult
import com.tana.spa.feature.auth.domain.model.OtpVerifyResult
import com.tana.spa.feature.auth.domain.usecase.GetCurrentSessionUseCase
import com.tana.spa.feature.auth.domain.usecase.SendPhoneOtpUseCase
import com.tana.spa.feature.auth.domain.usecase.VerifyPhoneOtpUseCase
import com.tana.spa.feature.auth.presentation.manipulator.VerifyMessage
import com.tana.spa.feature.auth.presentation.manipulator.VerifyPhoneEffect
import com.tana.spa.feature.auth.presentation.manipulator.VerifyPhoneEvent
import com.tana.spa.feature.auth.presentation.manipulator.VerifyPhoneViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Uses a virtual clock so the resend countdown can be stepped second by second. */
@OptIn(ExperimentalCoroutinesApi::class)
class VerifyPhoneViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val repository = FakeAuthRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): VerifyPhoneViewModel = VerifyPhoneViewModel(
        SendPhoneOtpUseCase(repository),
        VerifyPhoneOtpUseCase(repository),
        GetCurrentSessionUseCase(repository),
    ).also { scheduler.runCurrent() }

    @Test
    fun aCodeIsSentAutomaticallyOnOpenAndTheCountdownStarts() {
        val vm = viewModel()

        assertEquals(1, repository.sendCalls)
        val s = vm.state.value
        assertEquals("+84••••••969", s.maskedPhone)
        assertEquals(10, s.expiresInMinutes)
        assertFalse(s.isSending)
        assertEquals(60, s.resendSecondsLeft)
    }

    @Test
    fun theCountdownTicksDownAndUnlocksResend() {
        val vm = viewModel()

        scheduler.advanceTimeBy(1_000)
        scheduler.runCurrent()
        assertEquals(59, vm.state.value.resendSecondsLeft)
        scheduler.advanceTimeBy(58_000)
        scheduler.runCurrent()
        assertEquals(1, vm.state.value.resendSecondsLeft)
        scheduler.advanceTimeBy(1_000)
        scheduler.runCurrent()
        assertEquals(0, vm.state.value.resendSecondsLeft)
    }

    @Test
    fun resendIsIgnoredDuringTheCountdownThenSendsANewCode() {
        val vm = viewModel()
        vm.onEvent(VerifyPhoneEvent.Resend)
        scheduler.runCurrent()
        assertEquals(1, repository.sendCalls, "resend must wait for the countdown")

        scheduler.advanceTimeBy(60_000)
        scheduler.runCurrent()
        vm.onEvent(VerifyPhoneEvent.Resend)
        scheduler.runCurrent()
        assertEquals(2, repository.sendCalls)
        assertEquals(60, vm.state.value.resendSecondsLeft)
    }

    @Test
    fun typingSixDigitsSubmitsAutomaticallyAndSucceeds() = runTest(StandardTestDispatcher(scheduler)) {
        val vm = viewModel()
        vm.onEvent(VerifyPhoneEvent.CodeChanged("123456"))
        scheduler.runCurrent()

        assertEquals(listOf("123456"), repository.verifyCodes)
        assertEquals(VerifyPhoneEffect.NavigateHome, vm.effects.first())
    }

    @Test
    fun theCodeIsCleanedToDigitsAndCappedAtSix() {
        val vm = viewModel()
        vm.onEvent(VerifyPhoneEvent.CodeChanged("12 34-5"))
        assertEquals("12345", vm.state.value.code)
        assertTrue(repository.verifyCodes.isEmpty(), "five digits must not submit")

        vm.onEvent(VerifyPhoneEvent.CodeChanged("1234567890"))
        scheduler.runCurrent()
        assertEquals(listOf("123456"), repository.verifyCodes)
    }

    @Test
    fun aWrongCodeClearsTheFieldAndReportsAttemptsLeft() {
        repository.verifyResults += OtpVerifyResult.WrongCode(attemptsRemaining = 4)
        val vm = viewModel()
        vm.onEvent(VerifyPhoneEvent.CodeChanged("000000"))
        scheduler.runCurrent()

        val s = vm.state.value
        assertEquals("", s.code)
        assertEquals(VerifyMessage.WrongCode(4), s.message)
        assertFalse(s.isVerifying)

        // Typing again clears the message.
        vm.onEvent(VerifyPhoneEvent.CodeChanged("1"))
        assertNull(vm.state.value.message)
    }

    @Test
    fun anExpiredOrExhaustedCodeAsksForANewOne() {
        repository.verifyResults += OtpVerifyResult.Failed(OtpFailure.EXPIRED)
        val vm = viewModel()
        vm.onEvent(VerifyPhoneEvent.CodeChanged("111111"))
        scheduler.runCurrent()

        assertEquals(VerifyMessage.Failure(OtpFailure.EXPIRED), vm.state.value.message)
        assertEquals("", vm.state.value.code)
    }

    @Test
    fun anExpiredSessionSendsTheUserBackToLogin() = runTest(StandardTestDispatcher(scheduler)) {
        repository.verifyResults += OtpVerifyResult.Failed(OtpFailure.SESSION_EXPIRED)
        val vm = viewModel()
        vm.onEvent(VerifyPhoneEvent.CodeChanged("111111"))
        scheduler.runCurrent()

        assertEquals(VerifyPhoneEffect.NavigateToLogin, vm.effects.first())
    }

    @Test
    fun noSessionAtAllGoesStraightToLogin() = runTest(StandardTestDispatcher(scheduler)) {
        repository.session = null
        val vm = viewModel()

        assertEquals(VerifyPhoneEffect.NavigateToLogin, vm.effects.first())
        assertEquals(0, repository.sendCalls, "nothing may be sent without a login")
    }

    @Test
    fun anAlreadyVerifiedAccountSkipsTheScreen() = runTest(StandardTestDispatcher(scheduler)) {
        repository.session = TestFixtures.session.copy(user = TestFixtures.user.copy(phoneVerified = true))
        val vm = viewModel()

        assertEquals(VerifyPhoneEffect.NavigateHome, vm.effects.first())
        assertEquals(0, repository.sendCalls)
    }

    @Test
    fun aRecentCodeShowsACountdownInsteadOfAnError() {
        // Re-opening the screen within the cooldown: the server says "wait 42s".
        repository.sendResults += OtpSendResult.RateLimited(retryAfterSeconds = 42)
        val vm = viewModel()

        val s = vm.state.value
        assertEquals(42, s.resendSecondsLeft)
        assertNull(s.message)
        assertNull(s.maskedPhone)
        assertFalse(s.isSending)
    }

    @Test
    fun aLongRateLimitIsAMessageNotAnHourLongCountdown() {
        repository.sendResults += OtpSendResult.RateLimited(retryAfterSeconds = 3600)
        val vm = viewModel()

        assertEquals(0, vm.state.value.resendSecondsLeft)
        assertEquals(VerifyMessage.RateLimited, vm.state.value.message)
    }

    @Test
    fun aSendFailureIsShownAndCanBeRetriedImmediately() {
        repository.sendResults += OtpSendResult.Failed(OtpFailure.UNAVAILABLE)
        val vm = viewModel()

        assertEquals(VerifyMessage.Failure(OtpFailure.UNAVAILABLE), vm.state.value.message)
        assertEquals(0, vm.state.value.resendSecondsLeft)

        vm.onEvent(VerifyPhoneEvent.Resend)
        scheduler.runCurrent()
        assertEquals(2, repository.sendCalls)
        assertNull(vm.state.value.message)
    }

    @Test
    fun skipLeavesWithoutVerifying() = runTest(StandardTestDispatcher(scheduler)) {
        val vm = viewModel()
        vm.onEvent(VerifyPhoneEvent.Skip)
        scheduler.runCurrent()

        assertEquals(VerifyPhoneEffect.NavigateHome, vm.effects.first())
        assertTrue(repository.verifyCodes.isEmpty())
    }

    @Test
    fun anIncompleteCodeCannotBeSubmitted() {
        val vm = viewModel()
        vm.onEvent(VerifyPhoneEvent.CodeChanged("123"))
        vm.onEvent(VerifyPhoneEvent.Submit)
        scheduler.runCurrent()

        assertTrue(repository.verifyCodes.isEmpty())
    }
}
