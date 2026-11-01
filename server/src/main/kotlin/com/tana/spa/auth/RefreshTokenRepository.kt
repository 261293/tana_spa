package com.tana.spa.auth

import java.util.UUID

/**
 * A stored refresh token. The raw token itself is never kept: only the SHA-256 of it, so a reader
 * of the table cannot sign in as anybody.
 *
 * [familyId] ties every token descended from one sign-in together. [replaced] marks a token that
 * has already been spent — presenting it again means either an attacker replaying a stolen token
 * or a client that kept a copy, and neither is safe to serve.
 */
data class StoredRefreshToken(
    val id: String,
    val userId: String,
    val familyId: String,
    val expiresAtMillis: Long,
    val revoked: Boolean,
    val replaced: Boolean,
)

interface RefreshTokenRepository {
    /** @return the id of the row written, which the token it replaces will point at. */
    fun insert(userId: String, familyId: String, tokenHash: ByteArray, expiresAtMillis: Long): String

    fun find(tokenHash: ByteArray): StoredRefreshToken?

    fun markReplaced(id: String, replacedById: String)

    /** Revokes every token of the family, spent or not: one sign-in's whole chain. */
    fun revokeFamily(familyId: String)

    /** Drops rows that can no longer authorise anything. */
    fun deleteExpiredBefore(millis: Long): Int
}

/** In-memory store for local development and fast tests; selected when `DATABASE_URL` is not set. */
class InMemoryRefreshTokenRepository : RefreshTokenRepository {
    private val byId = LinkedHashMap<String, StoredRefreshToken>()
    private val idByHash = HashMap<String, String>()

    @Synchronized
    override fun insert(userId: String, familyId: String, tokenHash: ByteArray, expiresAtMillis: Long): String {
        val row = StoredRefreshToken(
            id = UUID.randomUUID().toString(),
            userId = userId,
            familyId = familyId,
            expiresAtMillis = expiresAtMillis,
            revoked = false,
            replaced = false,
        )
        byId[row.id] = row
        idByHash[tokenHash.toKey()] = row.id
        return row.id
    }

    @Synchronized
    override fun find(tokenHash: ByteArray): StoredRefreshToken? = idByHash[tokenHash.toKey()]?.let { byId[it] }

    @Synchronized
    override fun markReplaced(id: String, replacedById: String) {
        byId[id]?.let { byId[id] = it.copy(replaced = true) }
    }

    @Synchronized
    override fun revokeFamily(familyId: String) {
        byId.values.filter { it.familyId == familyId }.forEach { byId[it.id] = it.copy(revoked = true) }
    }

    @Synchronized
    override fun deleteExpiredBefore(millis: Long): Int {
        val gone = byId.values.filter { it.expiresAtMillis < millis }.map { it.id }.toSet()
        byId.keys.removeAll(gone)
        idByHash.values.removeAll(gone)
        return gone.size
    }

    private fun ByteArray.toKey(): String = joinToString("") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
}
