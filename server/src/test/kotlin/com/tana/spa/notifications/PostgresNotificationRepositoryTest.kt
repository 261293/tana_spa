package com.tana.spa.notifications

import com.tana.spa.auth.CreateUserResult
import com.tana.spa.auth.PostgresUserRepository
import com.tana.spa.db.TestDatabase
import org.postgresql.util.PSQLException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs against the real schema, because the parts worth checking here are the ones SQL owns: the
 * paging order, the unique index that makes a token belong to one account, and the cascade.
 */
class PostgresNotificationRepositoryTest {

    private companion object {
        const val CHECK_VIOLATION = "23514"
    }

    private val dataSource = TestDatabase.dataSource
    private val repository = PostgresNotificationRepository(dataSource)
    private val users = PostgresUserRepository(dataSource)

    private fun newUser(): String {
        val id = UUID.randomUUID().toString().take(10)
        val phone = "+843" + (10_000_000 + (System.nanoTime() % 80_000_000L)).toString().takeLast(8)
        return assertIs<CreateUserResult.Created>(users.create("$id@example.com", "Lan", phone, "h")).user.id
    }

    private fun token() = "fcm-" + UUID.randomUUID().toString().replace("-", "")

    private fun registration(user: String, token: String, platform: String = "android") = DeviceRegistration(
        userId = user,
        token = token,
        platform = platform,
        provider = "fcm",
        locale = "vi",
        appVersion = "1.0.0",
    )

    private fun notification(user: String, title: String, type: String = "system", data: Map<String, String> = emptyMap()) =
        repository.insert(NewNotification(user, type, title, body = "body of $title", data = data))

    private fun exec(sql: String, vararg params: Any?) = dataSource.connection.use { c ->
        c.prepareStatement(sql).use { ps ->
            params.forEachIndexed { i, p -> ps.setObject(i + 1, p) }
            ps.execute()
        }
    }

    @Test
    fun aStoredNotificationComesBackWithItsDeepLinkPayload() {
        val user = newUser()
        val id = notification(user, "Đơn hàng", type = "order", data = mapOf("orderId" to "1234"))

        val row = repository.list(user, limit = 10, beforeEpochMicros = null).single()

        assertEquals(id, row.id)
        assertEquals("order", row.type)
        assertEquals("Đơn hàng", row.title)
        assertEquals(mapOf("orderId" to "1234"), row.data)
        assertFalse(row.read)
        assertEquals(1, repository.unreadCount(user))
    }

    @Test
    fun theListIsNewestFirstAndPagesWithTheCursor() {
        val user = newUser()
        val ids = (1..3).map { notification(user, "N$it") }

        val firstPage = repository.list(user, limit = 2, beforeEpochMicros = null)
        assertEquals(listOf("N3", "N2"), firstPage.map { it.title })

        val secondPage = repository.list(user, limit = 2, beforeEpochMicros = firstPage.last().createdAtEpochMicros)
        assertEquals(listOf("N1"), secondPage.map { it.title })
        assertEquals(ids.first(), secondPage.single().id)
    }

    @Test
    fun twoNotificationsInsideOneMillisecondBothSurvivePaging() {
        val user = newUser()

        // created_at is set by hand because this is the case the cursor's precision decides: a
        // millisecond-rounded cursor maps both rows onto the same value and loses the older one.
        fun at(title: String, timestamp: String) = exec(
            "INSERT INTO notifications (user_id, type, title, created_at) VALUES (?::uuid, 'system', ?, ?::timestamptz)",
            UUID.fromString(user),
            title,
            timestamp,
        )
        at("Older", "2026-10-11T08:00:00.000100Z")
        at("Newer", "2026-10-11T08:00:00.000900Z")

        val first = repository.list(user, limit = 1, beforeEpochMicros = null)
        assertEquals("Newer", first.single().title)

        val second = repository.list(user, limit = 1, beforeEpochMicros = first.single().createdAtEpochMicros)
        assertEquals("Older", second.single().title, "the second row in the same millisecond was skipped")
    }

    @Test
    fun markingOneReadIsIdempotentAndScopedToItsOwner() {
        val owner = newUser()
        val stranger = newUser()
        val id = notification(owner, "Mine")

        assertFalse(repository.markRead(stranger, id), "another account must not be able to clear it")
        assertTrue(repository.markRead(owner, id))
        assertFalse(repository.markRead(owner, id), "already read leaves nothing to change")
        assertEquals(0, repository.unreadCount(owner))
    }

    @Test
    fun anIdThatIsNotAUuidIsSimplyNotFound() {
        val user = newUser()

        assertFalse(repository.markRead(user, "not-a-uuid"))
        assertTrue(repository.list("not-a-uuid", limit = 10, beforeEpochMicros = null).isEmpty())
        assertEquals(0, repository.unreadCount("not-a-uuid"))
    }

    @Test
    fun readAllClearsOnlyTheCallersUnreadRows() {
        val user = newUser()
        val other = newUser()
        repeat(3) { notification(user, "N$it") }
        notification(other, "Theirs")

        assertEquals(3, repository.markAllRead(user))
        assertEquals(0, repository.markAllRead(user), "a second call has nothing left to do")
        assertEquals(1, repository.unreadCount(other))
    }

    @Test
    fun markPushedRecordsTheFanOutWithoutTouchingTheReadState() {
        val user = newUser()
        val id = notification(user, "Pushed")

        repository.markPushed(id)

        val pushedAt = dataSource.connection.use { c ->
            c.prepareStatement("SELECT sent_push_at FROM notifications WHERE id = ?::uuid").use { ps ->
                ps.setObject(1, UUID.fromString(id))
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getTimestamp(1)
                }
            }
        }
        assertTrue(pushedAt != null, "sent_push_at should have been set")
        assertEquals(1, repository.unreadCount(user), "a push does not mark anything read")
    }

    @Test
    fun registeringTheSameDeviceTwiceUpdatesTheOneRow() {
        val user = newUser()
        val token = token()

        repository.register(registration(user, token, platform = "android"))
        repository.register(registration(user, token, platform = "web"))

        val devices = repository.devicesOf(user)
        assertEquals(1, devices.size)
        assertEquals("web", devices.single().platform)
    }

    @Test
    fun aDeviceBelongsToWhoeverSignedInLast() {
        val first = newUser()
        val second = newUser()
        val token = token()

        repository.register(registration(first, token))
        repository.register(registration(second, token))

        assertTrue(repository.devicesOf(first).isEmpty(), "the previous user must stop receiving this device's pushes")
        assertEquals(token, repository.devicesOf(second).single().token)
    }

    @Test
    fun unregisteringAndBulkDeletingBothReportWhatTheyRemoved() {
        val user = newUser()
        val live = token()
        val dead = token()
        repository.register(registration(user, live))
        repository.register(registration(user, dead))

        assertTrue(repository.unregister(dead))
        assertFalse(repository.unregister(dead), "a token that is already gone is not an error")
        assertEquals(0, repository.deleteTokens(emptyList()))
        assertEquals(1, repository.deleteTokens(listOf(live, dead)))
        assertTrue(repository.devicesOf(user).isEmpty())
    }

    @Test
    fun theSchemaRefusesAnUnknownPlatformOrProvider() {
        val user = newUser()

        assertFailsWith<PSQLException> { repository.register(registration(user, token(), platform = "fridge")) }
            .let { assertEquals(CHECK_VIOLATION, it.sqlState, it.message) }
        assertFailsWith<PSQLException> {
            repository.register(registration(user, token()).copy(provider = "apns"))
        }.let { assertEquals(CHECK_VIOLATION, it.sqlState, it.message) }
    }

    @Test
    fun theSchemaRefusesTextLongerThanTheClientsAreAllowedToSend() {
        val user = newUser()

        assertFailsWith<PSQLException> {
            repository.insert(NewNotification(user, "system", "t".repeat(121), null, emptyMap()))
        }.let { assertEquals(CHECK_VIOLATION, it.sqlState, it.message) }
        assertFailsWith<PSQLException> {
            repository.insert(NewNotification(user, "system", "   ", null, emptyMap()))
        }.let { assertEquals(CHECK_VIOLATION, it.sqlState, it.message) }
        assertFailsWith<PSQLException> {
            repository.insert(NewNotification(user, "system", "ok", "b".repeat(501), emptyMap()))
        }.let { assertEquals(CHECK_VIOLATION, it.sqlState, it.message) }
    }

    @Test
    fun deletingAnAccountTakesItsDevicesAndNotificationsWithIt() {
        val user = newUser()
        notification(user, "Goes away")
        repository.register(registration(user, token()))

        // Hard delete: the app soft-deletes, but a GDPR erasure has to leave nothing behind.
        exec("DELETE FROM users WHERE id = ?::uuid", UUID.fromString(user))

        assertTrue(repository.devicesOf(user).isEmpty())
        assertTrue(repository.list(user, limit = 10, beforeEpochMicros = null).isEmpty())
    }

    @Test
    fun jsonbWrittenByHandIsFlattenedRatherThanFailingTheRead() {
        val user = newUser()
        exec(
            """INSERT INTO notifications (user_id, type, title, data)
               VALUES (?::uuid, 'promotion', 'Khuyến mãi', '{"percent": 20, "nested": {"a": 1}}'::jsonb)""",
            UUID.fromString(user),
        )

        val row = repository.list(user, limit = 1, beforeEpochMicros = null).single()

        assertEquals("20", row.data["percent"])
        assertEquals("""{"a":1}""", row.data["nested"])
        assertNull(row.data["missing"])
    }
}
