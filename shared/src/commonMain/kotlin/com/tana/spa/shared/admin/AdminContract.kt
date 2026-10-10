package com.tana.spa.shared.admin

import kotlinx.serialization.Serializable

object AdminApi {
    const val BASE = "/api/v1/admin"

    /** `GET ?q=<name, email or id>&limit=<n>`. */
    const val USERS = "$BASE/users"

    const val MAX_USER_RESULTS = 50
}

@Serializable
data class AdminUserDto(val id: String, val displayName: String, val email: String, val role: String)

@Serializable
data class AdminUsersResponse(val users: List<AdminUserDto>)
