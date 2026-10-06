package com.tana.spa.feature.auth.domain.usecase

import com.tana.spa.feature.auth.FakeRemote
import com.tana.spa.feature.auth.FakeSessionStore
import com.tana.spa.feature.auth.data.repository.AuthRepositoryImpl
import com.tana.spa.feature.auth.domain.model.RegisterResult
import com.tana.spa.shared.auth.EmailError
import com.tana.spa.shared.auth.NameError
import com.tana.spa.shared.auth.PasswordError
import com.tana.spa.shared.auth.PhoneError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RegisterUseCaseTest {

    private val remote = FakeRemote()
    private val useCase = RegisterUseCase(AuthRepositoryImpl(remote, FakeSessionStore()) { 0L })

    @Test
    fun anEmptyFormReportsEveryFieldAndNeverReachesTheNetwork() = runTest {
        val result = useCase("  ", "", "", "")

        assertEquals(
            RegisterResult.InvalidInput(NameError.EMPTY, EmailError.EMPTY, PhoneError.EMPTY, PasswordError.EMPTY),
            result,
        )
        assertEquals(0, remote.registerCalls)
    }

    @Test
    fun eachRuleIsReportedOnItsOwnField() = runTest {
        val result = useCase("Lan", "not-an-email", "12345", "short")

        assertEquals(
            RegisterResult.InvalidInput(null, EmailError.INVALID, PhoneError.INVALID, PasswordError.TOO_SHORT),
            result,
        )
    }

    @Test
    fun unlikeLoginTheFullPasswordRuleApplies() = runTest {
        val result = useCase("Lan", "lan@example.com", "0333433969", "1234567")
        assertEquals(RegisterResult.InvalidInput(null, null, null, PasswordError.TOO_SHORT), result)
    }

    @Test
    fun aValidFormIsSentTrimmedAndTheServerNormalisesThePhone() = runTest {
        val result = useCase("  Nguyễn Lê Phương Anh ", "  Lan@Example.COM ", " 0333 433 969 ", " spaced pass ")

        assertIs<RegisterResult.Success>(result)
        assertEquals(
            // name trimmed, email normalised, phone sent as typed (trimmed), password verbatim
            listOf("Nguyễn Lê Phương Anh", "lan@example.com", "0333 433 969", " spaced pass "),
            remote.lastRegister,
        )
    }
}
