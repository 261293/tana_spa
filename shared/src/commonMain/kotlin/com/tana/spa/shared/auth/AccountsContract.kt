package com.tana.spa.shared.auth

import kotlinx.serialization.Serializable

@Serializable
data class CreateAccountRequest(val displayName: String, val email: String, val password: String, val role: String)

@Serializable
data class CreatedAccountDto(val id: String, val displayName: String, val email: String, val role: String)

object AccountErrorCodes {
    const val ROLE_NOT_ALLOWED = "role_not_allowed"
    const val OWN_ROLE_UNCHANGEABLE = "own_role_unchangeable"
    const val UNKNOWN_ACCOUNT = "unknown_account"
}
