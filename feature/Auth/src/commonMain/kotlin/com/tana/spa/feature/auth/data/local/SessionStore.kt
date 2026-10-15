package com.tana.spa.feature.auth.data.local

import com.tana.spa.core.cache.KeyValueCache
import com.tana.spa.core.logging.AppLog
import com.tana.spa.feature.auth.domain.model.AuthUser
import com.tana.spa.feature.auth.domain.model.Session
import com.tana.spa.feature.auth.domain.model.UserRoleName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface SessionStore {
    fun save(session: Session)
    fun load(): Session?
    fun clear()
}

/**
 * Persists the session as JSON in the app's [KeyValueCache].
 *
 * Note: the cache is a plain SQLite file on Android/iOS and in-memory on web, so the token is
 * neither encrypted at rest nor kept across a browser reload. Move this behind Keystore /
 * Keychain-backed storage before shipping to production.
 */
class SessionStoreImpl(private val cache: KeyValueCache) : SessionStore {

    override fun save(session: Session) {
        cache.put(KEY, json.encodeToString(StoredSession.serializer(), session.toStored()))
    }

    override fun load(): Session? {
        val raw = cache.get(KEY)?.takeIf { it.isNotBlank() } ?: return null
        return try {
            json.decodeFromString(StoredSession.serializer(), raw).toDomain()
        } catch (e: Exception) {
            // Corrupt or from an incompatible app version: treat as logged out.
            AppLog.w("SessionStore", e) { "stored session is unreadable, discarding it" }
            cache.put(KEY, "")
            null
        }
    }

    override fun clear() {
        AppLog.d("SessionStore") { "session cleared" }
        cache.put(KEY, "")
    }

    private companion object {
        const val KEY = "auth.session"
        val json = Json { ignoreUnknownKeys = true }
    }
}

// New fields have defaults so a session saved by an older app version still loads.
@Serializable
private data class StoredSession(
    val accessToken: String,
    val expiresAtMillis: Long,
    val userId: String,
    val email: String,
    val displayName: String,
    val phone: String? = null,
    val phoneVerified: Boolean = false,
    val role: String = UserRoleName.CUSTOMER.wire,
)

private fun Session.toStored() = StoredSession(
    accessToken = accessToken,
    expiresAtMillis = expiresAtMillis,
    userId = user.id,
    email = user.email,
    displayName = user.displayName,
    phone = user.phone,
    phoneVerified = user.phoneVerified,
    role = user.role.wire,
)

private fun StoredSession.toDomain() = Session(
    accessToken = accessToken,
    expiresAtMillis = expiresAtMillis,
    user = AuthUser(userId, email, displayName, phone, phoneVerified, UserRoleName.fromWire(role)),
)
