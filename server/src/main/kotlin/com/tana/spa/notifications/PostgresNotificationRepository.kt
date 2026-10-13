package com.tana.spa.notifications

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

/** Schema: `V4__engagement.sql` (notifications) and `V8__push_notifications.sql` (device_tokens). */
class PostgresNotificationRepository(private val dataSource: DataSource) : NotificationRepository {

    override fun list(userId: String, limit: Int, beforeEpochMicros: Long?): List<NotificationRow> {
        val user = userId.asUuid() ?: return emptyList()
        return dataSource.connection.use { c ->
            c.prepareStatement(
                """SELECT id, type, title, body, data, read_at, created_at
                   FROM notifications
                   WHERE user_id = ? AND (?::timestamptz IS NULL OR created_at < ?::timestamptz)
                   ORDER BY created_at DESC
                   LIMIT ?""",
            ).use { ps ->
                // Passed at microsecond precision, exactly as the column stores it, so the
                // strict `<` cannot step over a row that shares a millisecond with the cursor.
                val before = beforeEpochMicros?.let(::offsetDateTimeOf)
                ps.setObject(1, user)
                ps.setObject(2, before)
                ps.setObject(3, before)
                ps.setInt(4, limit)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toNotificationRow()) } }
            }
        }
    }

    override fun unreadCount(userId: String): Int {
        val user = userId.asUuid() ?: return 0
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT count(*) FROM notifications WHERE user_id = ? AND read_at IS NULL").use { ps ->
                ps.setObject(1, user)
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }
    }

    override fun markRead(userId: String, notificationId: String): Boolean {
        val user = userId.asUuid() ?: return false
        val id = notificationId.asUuid() ?: return false
        return dataSource.connection.use { c ->
            c.prepareStatement(
                "UPDATE notifications SET read_at = now() WHERE id = ? AND user_id = ? AND read_at IS NULL",
            ).use { ps ->
                ps.setObject(1, id)
                ps.setObject(2, user)
                ps.executeUpdate() == 1
            }
        }
    }

    override fun markAllRead(userId: String): Int {
        val user = userId.asUuid() ?: return 0
        return dataSource.connection.use { c ->
            c.prepareStatement("UPDATE notifications SET read_at = now() WHERE user_id = ? AND read_at IS NULL").use { ps ->
                ps.setObject(1, user)
                ps.executeUpdate()
            }
        }
    }

    override fun insert(notification: NewNotification): String {
        val user = requireNotNull(notification.userId.asUuid()) { "userId is not a uuid" }
        return dataSource.connection.use { c ->
            c.prepareStatement(
                """INSERT INTO notifications (user_id, type, title, body, data)
                   VALUES (?, ?, ?, ?, ?::jsonb)
                   RETURNING id""",
            ).use { ps ->
                ps.setObject(1, user)
                ps.setString(2, notification.type)
                ps.setString(3, notification.title)
                ps.setString(4, notification.body)
                ps.setString(5, json.encodeToString(notification.data))
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getObject(1, UUID::class.java).toString()
                }
            }
        }
    }

    override fun markPushed(notificationId: String) {
        val id = notificationId.asUuid() ?: return
        dataSource.connection.use { c ->
            c.prepareStatement("UPDATE notifications SET sent_push_at = now() WHERE id = ?").use { ps ->
                ps.setObject(1, id)
                ps.executeUpdate()
            }
        }
    }

    override fun devicesOf(userId: String): List<DeviceTokenRow> {
        val user = userId.asUuid() ?: return emptyList()
        return dataSource.connection.use { c ->
            c.prepareStatement(
                "SELECT token, platform, provider, locale FROM device_tokens WHERE user_id = ? ORDER BY last_seen_at DESC",
            ).use { ps ->
                ps.setObject(1, user)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                DeviceTokenRow(
                                    token = rs.getString("token"),
                                    platform = rs.getString("platform"),
                                    provider = rs.getString("provider"),
                                    locale = rs.getString("locale"),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun register(registration: DeviceRegistration) {
        val user = requireNotNull(registration.userId.asUuid()) { "userId is not a uuid" }
        dataSource.connection.use { c ->
            // The token is the conflict target, not (user_id, token): one install has one owner,
            // and signing in as somebody else has to move it rather than add a second row.
            c.prepareStatement(
                """INSERT INTO device_tokens (user_id, token, platform, provider, locale, app_version)
                   VALUES (?, ?, ?, ?, ?, ?)
                   ON CONFLICT (token) DO UPDATE SET
                       user_id = excluded.user_id,
                       platform = excluded.platform,
                       provider = excluded.provider,
                       locale = excluded.locale,
                       app_version = excluded.app_version,
                       last_seen_at = now()""",
            ).use { ps ->
                ps.setObject(1, user)
                ps.setString(2, registration.token)
                ps.setString(3, registration.platform)
                ps.setString(4, registration.provider)
                ps.setString(5, registration.locale)
                ps.setString(6, registration.appVersion)
                ps.executeUpdate()
            }
        }
    }

    override fun unregister(token: String): Boolean = dataSource.connection.use { c ->
        c.prepareStatement("DELETE FROM device_tokens WHERE token = ?").use { ps ->
            ps.setString(1, token)
            ps.executeUpdate() == 1
        }
    }

    override fun deleteTokens(tokens: Collection<String>): Int {
        if (tokens.isEmpty()) return 0
        return dataSource.connection.use { c ->
            c.prepareStatement("DELETE FROM device_tokens WHERE token = ANY (?)").use { ps ->
                ps.setArray(1, c.createArrayOf("text", tokens.toTypedArray()))
                ps.executeUpdate()
            }
        }
    }

    private fun ResultSet.toNotificationRow() = NotificationRow(
        id = getObject("id", UUID::class.java).toString(),
        type = getString("type"),
        title = getString("title"),
        body = getString("body"),
        data = readData(getString("data")),
        read = getObject("read_at", OffsetDateTime::class.java) != null,
        createdAtEpochMicros = getObject("created_at", OffsetDateTime::class.java).toEpochMicros(),
    )

    /**
     * `data` is jsonb written by other producers too (a trigger, a migration, an operator), so a
     * number or a nested object is flattened to its JSON text rather than failing the whole read.
     */
    private fun readData(raw: String?): Map<String, String> {
        val text = raw?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val parsed = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyMap()
        return parsed.mapValues { (_, value) -> (value as? JsonPrimitive)?.content ?: value.toString() }
    }

    private fun String.asUuid(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

    private fun OffsetDateTime.toEpochMicros(): Long = toInstant().let { it.epochSecond * 1_000_000 + it.nano / 1_000 }

    private fun offsetDateTimeOf(epochMicros: Long): OffsetDateTime = OffsetDateTime.ofInstant(
        Instant.ofEpochSecond(
            Math.floorDiv(epochMicros, 1_000_000L),
            Math.floorMod(epochMicros, 1_000_000L) * 1_000L,
        ),
        ZoneOffset.UTC,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
