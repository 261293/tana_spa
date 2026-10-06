package com.tana.spa.feature.auth.presentation

import com.tana.spa.feature.auth.FakeAuthRepository
import com.tana.spa.feature.auth.domain.model.AuthFailure
import com.tana.spa.feature.auth.domain.repository.AuthOutcome
import com.tana.spa.feature.auth.domain.usecase.RegisterUseCase
import com.tana.spa.feature.auth.presentation.manipulator.ComingSoon
import com.tana.spa.feature.auth.presentation.manipulator.SignUpEffect
import com.tana.spa.feature.auth.presentation.manipulator.SignUpEvent
import com.tana.spa.feature.auth.presentation.manipulator.SignUpViewModel
import com.tana.spa.shared.auth.EmailError
import com.tana.spa.shared.auth.NameError
import com.tana.spa.shared.auth.PasswordError
import com.tana.spa.shared.auth.PhoneError
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SignUpViewModelTest {

    private val repository = FakeAuthRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SignUpViewModel(RegisterUseCase(repository))

    private fun SignUpViewModel.fill(
        name: String = "Lan",
        email: String = "lan@example.com",
        phone: String = "0333 433 969",
        password: String = "password123",
    ) {
        onEvent(SignUpEvent.NameChanged(name))
        onEvent(SignUpEvent.EmailChanged(email))
        onEvent(SignUpEvent.PhoneChanged(phone))
        onEvent(SignUpEvent.PasswordChanged(password))
    }

    @Test
    fun successfulSignUpMovesOnToPhoneVerification() = runTest {
        val vm = viewModel()
        vm.fill()
        vm.onEvent(SignUpEvent.Submit)

        assertEquals(SignUpEffect.NavigateToVerifyPhone, vm.effects.first())
        assertFalse(vm.state.value.isLoading)
        assertEquals(1, repository.registerCalls)
    }

    @Test
    fun anEmptyFormShowsEveryFieldErrorWithoutCallingTheServer() {
        val vm = viewModel()
        vm.onEvent(SignUpEvent.Submit)

        val s = vm.state.value
        assertEquals(NameError.EMPTY, s.nameError)
        assertEquals(EmailError.EMPTY, s.emailError)
        assertEquals(PhoneError.EMPTY, s.phoneError)
        assertEquals(PasswordError.EMPTY, s.passwordError)
        assertEquals(0, repository.registerCalls)
        assertFalse(s.isLoading)
    }

    @Test
    fun editingAFieldClearsOnlyThatFieldsError() {
        val vm = viewModel()
        vm.onEvent(SignUpEvent.Submit)
        vm.onEvent(SignUpEvent.PhoneChanged("0"))

        assertNull(vm.state.value.phoneError)
        assertEquals(NameError.EMPTY, vm.state.value.nameError)
        assertEquals(PasswordError.EMPTY, vm.state.value.passwordError)
    }

    @Test
    fun serverRejectionsAreShownAndClearedByTyping() {
        repository.registerOutcome = AuthOutcome.Failure(AuthFailure.EMAIL_TAKEN)
        val vm = viewModel()
        vm.fill()
        vm.onEvent(SignUpEvent.Submit)

        assertEquals(AuthFailure.EMAIL_TAKEN, vm.state.value.failure)
        assertFalse(vm.state.value.isLoading)

        vm.onEvent(SignUpEvent.EmailChanged("other@example.com"))
        assertNull(vm.state.value.failure)
    }

    @Test
    fun aRequestInFlightCannotBeSubmittedAgain() {
        val gate = CompletableDeferred<Unit>()
        repository.registerGate = gate
        val vm = viewModel()
        vm.fill()

        vm.onEvent(SignUpEvent.Submit)
        assertTrue(vm.state.value.isLoading)
        vm.onEvent(SignUpEvent.Submit) // double tap / IME "Done" while the first is still running
        vm.onEvent(SignUpEvent.Submit)
        assertEquals(1, repository.registerCalls, "only one account may be created")

        gate.complete(Unit)
        assertFalse(vm.state.value.isLoading)
        assertEquals(1, repository.registerCalls)
    }

    @Test
    fun passwordVisibilityToggles() {
        val vm = viewModel()
        assertFalse(vm.state.value.passwordVisible)
        vm.onEvent(SignUpEvent.TogglePasswordVisibility)
        assertTrue(vm.state.value.passwordVisible)
    }

    @Test
    fun loginLinkAndGoogleButtonEmitTheirEffects() = runTest {
        val vm = viewModel()
        vm.onEvent(SignUpEvent.LoginClicked)
        assertEquals(SignUpEffect.NavigateToLogin, vm.effects.first())
        vm.onEvent(SignUpEvent.GoogleClicked)
        assertEquals(SignUpEffect.ShowComingSoon(ComingSoon.GOOGLE), vm.effects.first())
    }
}
