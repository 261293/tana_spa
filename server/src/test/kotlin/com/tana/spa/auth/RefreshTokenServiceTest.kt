package com.tana.spa.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

class RefreshTokenServiceTest {

    private val users = InMemoryUserRepository()
    private val store = InMemoryRefreshTokenRepository()
    private var now = 1_000_000L
    private var issued = 0

    private val user = requireNonNull(users.ensureSeeded("lan@example.com", "Lan", "hash", UserRole.CUSTOMER))

    private val service = RefreshTokenService(
        tokens = store,
        users = users,
        ttlMillis = 30 * DAY_MILLIS,
        nowMillis = { now },
        newToken = { "token-${issued++}" },
    )

    @Test
    fun rotating_returns_a_different_token_and_the_account_it_belongs_to() {
        val first = service.issue(user.id)

        val outcome = assertIs<RotateOutcome.Rotated>(service.rotate(first.token))

        assertEquals(user.id, outcome.user.id)
        assertNotEquals(first.token, outcome.refresh.token)
    }

    @Test
    fun the_replacement_keeps_working_after_the_token_it_replaced_is_spent() {
        val first = service.issue(user.id)
        val second = assertIs<RotateOutcome.Rotated>(service.rotate(first.token)).refresh

        assertIs<RotateOutcome.Rotated>(service.rotate(second.token))
    }

    @Test
    fun a_token_presented_twice_is_a_replay_and_kills_the_whole_family() {
        val first = service.issue(user.id)
        val second = assertIs<RotateOutcome.Rotated>(service.rotate(first.token)).refresh

        assertIs<RotateOutcome.Reused>(service.rotate(first.token))

        assertIs<RotateOutcome.Rejected>(service.rotate(second.token))
    }

    @Test
    fun one_sign_ins_replay_does_not_touch_another_sign_in() {
        val phone = service.issue(user.id)
        val laptop = service.issue(user.id)
        service.rotate(phone.token)

        assertIs<RotateOutcome.Reused>(service.rotate(phone.token))

        assertIs<RotateOutcome.Rotated>(service.rotate(laptop.token))
    }

    @Test
    fun an_unknown_token_is_rejected_without_revoking_anything() {
        val mine = service.issue(user.id)

        assertIs<RotateOutcome.Rejected>(service.rotate("never-issued"))

        assertIs<RotateOutcome.Rotated>(service.rotate(mine.token))
    }

    @Test
    fun a_token_is_rejected_the_moment_its_window_closes() {
        val token = service.issue(user.id)

        now = token.expiresAtMillis - 1
        assertIs<RotateOutcome.Rotated>(service.rotate(token.token))

        val next = service.issue(user.id)
        now = next.expiresAtMillis
        assertIs<RotateOutcome.Rejected>(service.rotate(next.token))
    }

    @Test
    fun signing_out_revokes_the_family_rather_than_the_one_token_presented() {
        val first = service.issue(user.id)
        val second = assertIs<RotateOutcome.Rotated>(service.rotate(first.token)).refresh

        service.revoke(second.token)

        assertIs<RotateOutcome.Rejected>(service.rotate(second.token))
    }

    @Test
    fun revoking_something_that_was_never_issued_changes_nothing() {
        val mine = service.issue(user.id)

        service.revoke("never-issued")

        assertIs<RotateOutcome.Rotated>(service.rotate(mine.token))
    }

    @Test
    fun a_token_outlives_the_account_no_longer_than_the_account_itself() {
        val gone = requireNonNull(users.ensureSeeded("gone@example.com", "Gone", "hash", UserRole.CUSTOMER))
        val token = service.issue("not-a-user-id")

        assertIs<RotateOutcome.Rejected>(service.rotate(token.token))
        assertSame(gone.id, users.findById(gone.id)?.id)
    }

    @Test
    fun the_raw_token_is_never_what_gets_stored() {
        val token = service.issue(user.id)

        assertEquals(null, store.find(token.token.encodeToByteArray()))
        assertNotEquals(null, store.find(hash(token.token)))
    }

    @Test
    fun housekeeping_drops_only_what_can_no_longer_authorise_anything() {
        val stale = service.issue(user.id)
        now += 31 * DAY_MILLIS
        val fresh = service.issue(user.id)

        assertEquals(1, service.deleteExpired())

        assertIs<RotateOutcome.Rejected>(service.rotate(stale.token))
        assertIs<RotateOutcome.Rotated>(service.rotate(fresh.token))
    }
}

private fun <T : Any> requireNonNull(value: T?): T = requireNotNull(value)
