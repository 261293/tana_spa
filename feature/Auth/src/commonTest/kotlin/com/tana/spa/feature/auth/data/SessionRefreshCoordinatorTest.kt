package com.tana.spa.feature.auth.data

import com.tana.spa.feature.auth.TestFixtures
import com.tana.spa.feature.auth.domain.model.OtpSendResult
import com.tana.spa.feature.auth.domain.model.OtpVerifyResult
import com.tana.spa.feature.auth.domain.model.Session
import com.tana.spa.feature.auth.domain.repository.AuthOutcome
import com.tana.spa.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val STALE = "token"

class SessionRefreshCoordinatorTest {

    @Test
    fun calls_refused_at_the_same_moment_spend_the_refresh_token_once() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = RecordingRepository(gate)
        val coordinator = SessionRefreshCoordinator(repository)

        val first = async { coordinator.refresh(STALE) }
        val second = async { coordinator.refresh(STALE) }
        val third = async { coordinator.refresh(STALE) }
        runCurrent()
        gate.complete(Unit)

        assertEquals("fresh-1", first.await())
        assertEquals("fresh-1", second.await())
        assertEquals("fresh-1", third.await())
        assertEquals(1, repository.refreshes, "a rotated token spent twice revokes the whole sign-in")
    }

    @Test
    fun a_call_still_holding_the_current_token_does_the_refresh_itself() = runTest {
        val repository = RecordingRepository()
        val coordinator = SessionRefreshCoordinator(repository)

        assertEquals("fresh-1", coordinator.refresh(STALE))
        assertEquals(1, repository.refreshes)
    }

    @Test
    fun a_later_call_refreshes_again_once_its_own_token_goes_stale() = runTest {
        val repository = RecordingRepository()
        val coordinator = SessionRefreshCoordinator(repository)

        assertEquals("fresh-1", coordinator.refresh(STALE))
        assertEquals("fresh-2", coordinator.refresh("fresh-1"))
        assertEquals(2, repository.refreshes)
    }

    @Test
    fun a_refusal_that_cannot_be_refreshed_reports_no_token() = runTest {
        val repository = RecordingRepository().apply { refreshable = false }
        val coordinator = SessionRefreshCoordinator(repository)

        assertNull(coordinator.refresh(STALE))
    }
}

private class RecordingRepository(private val gate: CompletableDeferred<Unit>? = null) : AuthRepository {
    var refreshes = 0
    var refreshable = true
    private var current: Session? = TestFixtures.session.copy(accessToken = STALE)

    override suspend fun refreshSession(): Session? {
        refreshes++
        gate?.await()
        if (!refreshable) {
            current = null
            return null
        }
        return TestFixtures.session.copy(accessToken = "fresh-$refreshes").also { current = it }
    }

    override fun currentSession(): Session? = current

    override suspend fun revokeRefreshToken(refreshToken: String) = Unit

    override suspend fun login(email: String, password: String): AuthOutcome = AuthOutcome.Success(TestFixtures.session)

    override suspend fun register(displayName: String, email: String, phone: String, password: String): AuthOutcome =
        AuthOutcome.Success(TestFixtures.session)

    override suspend fun sendPhoneOtp(): OtpSendResult = OtpSendResult.Sent(60, 600, "+84••••••969")

    override suspend fun verifyPhoneOtp(code: String): OtpVerifyResult = OtpVerifyResult.Verified
}
