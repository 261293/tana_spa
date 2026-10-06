package com.tana.spa.feature.auth.presentation

import com.tana.spa.feature.auth.FakeRemote
import com.tana.spa.feature.auth.FakeSessionStore
import com.tana.spa.feature.auth.TestFixtures
import com.tana.spa.feature.auth.data.remote.AuthApiException
import com.tana.spa.feature.auth.data.repository.AuthRepositoryImpl
import com.tana.spa.feature.auth.domain.model.AuthFailure
import com.tana.spa.feature.auth.domain.usecase.GetCurrentSessionUseCase
import com.tana.spa.feature.auth.domain.usecase.LoginUseCase
import com.tana.spa.feature.auth.presentation.manipulator.ComingSoon
import com.tana.spa.feature.auth.presentation.manipulator.LoginEffect
import com.tana.spa.feature.auth.presentation.manipulator.LoginEvent
import com.tana.spa.feature.auth.presentation.manipulator.LoginViewModel
import com.tana.spa.shared.auth.EmailError
import com.tana.spa.shared.auth.PasswordError
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
class LoginViewModelTest {

    private val remote = FakeRemote()
    private val store = FakeSessionStore()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): LoginViewModel {
        val repository = AuthRepositoryImpl(remote, store) { 0L }
        return LoginViewModel(LoginUseCase(repository), GetCurrentSessionUseCase(repository))
    }

    private fun LoginViewModel.fill(email: String = "lan@example.com", password: String = "password123") {
        onEvent(LoginEvent.EmailChanged(email))
        onEvent(LoginEvent.PasswordChanged(password))
    }

    @Test
    fun successfulLoginNavigatesHome() = runTest {
        val vm = viewModel()
        vm.fill()
        vm.onEvent(LoginEvent.Submit)

        assertEquals(LoginEffect.NavigateHome, vm.effects.first())
        assertFalse(vm.state.value.isLoading)
        assertEquals(TestFixtures.response.accessToken, store.stored?.accessToken)
    }

    @Test
    fun emptyFormShowsFieldErrorsWithoutCallingServer() {
        val vm = viewModel()
        vm.onEvent(LoginEvent.Submit)

        assertEquals(EmailError.EMPTY, vm.state.value.emailError)
        assertEquals(PasswordError.EMPTY, vm.state.value.passwordError)
        assertEquals(0, remote.calls)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun wrongCredentialsShowFailureAndTypingClearsIt() {
        remote.error = AuthApiException(401, "invalid_credentials")
        val vm = viewModel()
        vm.fill()
        vm.onEvent(LoginEvent.Submit)

        assertEquals(AuthFailure.INVALID_CREDENTIALS, vm.state.value.failure)
        assertFalse(vm.state.value.isLoading)

        vm.onEvent(LoginEvent.PasswordChanged("other"))
        assertNull(vm.state.value.failure)
    }

    @Test
    fun editingAFieldClearsOnlyThatFieldsError() {
        val vm = viewModel()
        vm.onEvent(LoginEvent.Submit)
        vm.onEvent(LoginEvent.EmailChanged("a"))

        assertNull(vm.state.value.emailError)
        assertEquals(PasswordError.EMPTY, vm.state.value.passwordError)
    }

    @Test
    fun existingSessionSkipsTheForm() = runTest {
        store.stored = TestFixtures.session.copy(expiresAtMillis = 1)
        assertEquals(LoginEffect.NavigateHome, viewModel().effects.first())
    }

    @Test
    fun expiredSessionShowsTheForm() {
        store.stored = TestFixtures.session.copy(expiresAtMillis = 0)
        val vm = viewModel()
        assertNull(store.stored)
        assertEquals("", vm.state.value.email)
    }

    @Test
    fun passwordVisibilityToggles() {
        val vm = viewModel()
        assertFalse(vm.state.value.passwordVisible)
        vm.onEvent(LoginEvent.TogglePasswordVisibility)
        assertTrue(vm.state.value.passwordVisible)
    }

    @Test
    fun googleIsComingSoonAndSignUpNavigates() = runTest {
        val vm = viewModel()
        vm.onEvent(LoginEvent.GoogleClicked)
        assertEquals(LoginEffect.ShowComingSoon(ComingSoon.GOOGLE), vm.effects.first())
        vm.onEvent(LoginEvent.SignUpClicked)
        assertEquals(LoginEffect.NavigateToSignUp, vm.effects.first())
    }
}
