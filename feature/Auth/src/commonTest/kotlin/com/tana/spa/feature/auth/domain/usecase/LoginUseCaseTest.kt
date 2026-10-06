package com.tana.spa.feature.auth.domain.usecase

import com.tana.spa.feature.auth.FakeRemote
import com.tana.spa.feature.auth.FakeSessionStore
import com.tana.spa.feature.auth.data.repository.AuthRepositoryImpl
import com.tana.spa.feature.auth.domain.model.LoginResult
import com.tana.spa.shared.auth.EmailError
import com.tana.spa.shared.auth.PasswordError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LoginUseCaseTest {

    private val remote = FakeRemote()
    private val useCase = LoginUseCase(AuthRepositoryImpl(remote, FakeSessionStore()) { 0L })

    @Test
    fun invalidFormNeverReachesTheNetwork() = runTest {
        val result = useCase("not-an-email", "")

        assertEquals(LoginResult.InvalidInput(EmailError.INVALID, PasswordError.EMPTY), result)
        assertEquals(0, remote.calls)
    }

    @Test
    fun emptyEmailIsReported() = runTest {
        assertEquals(LoginResult.InvalidInput(EmailError.EMPTY, null), useCase("  ", "secret"))
    }

    @Test
    fun emailIsNormalisedAndPasswordSentVerbatim() = runTest {
        val result = useCase("  Lan@Example.COM ", " pass word ")

        assertIs<LoginResult.Success>(result)
        assertEquals("lan@example.com", remote.lastEmail)
        assertEquals(" pass word ", remote.lastPassword)
    }

    @Test
    fun shortPasswordIsStillAllowedToTryLogin() = runTest {
        assertIs<LoginResult.Success>(useCase("lan@example.com", "abc"))
    }
}
