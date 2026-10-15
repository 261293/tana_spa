package com.tana.spa.admin

import com.tana.spa.auth.AuthService
import com.tana.spa.auth.User
import com.tana.spa.auth.UserRole
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.tana.spa.admin")

/** Operating the system: feature flags and discount codes. */
internal suspend fun ApplicationCall.requireAdmin(auth: AuthService): User? = requireRole(auth, UserRole.ADMIN)

/** Running the spa: the booking queue, the decisions and the menu. */
internal suspend fun ApplicationCall.requireOwner(auth: AuthService): User? = requireRole(auth, UserRole.OWNER)

/**
 * Answers the caller and returns null when the request must not proceed. Every privileged route
 * goes through here, so a route cannot be left unguarded by a copy-paste that drops the check.
 *
 * The role is read from the store on every call rather than from a token claim, so revoking
 * someone's role takes effect at once instead of when their access token expires.
 */
private suspend fun ApplicationCall.requireRole(auth: AuthService, role: UserRole): User? {
    val user = principal<JWTPrincipal>()?.payload?.subject?.let { auth.findUser(it) }
    if (user == null) {
        respondApiError(HttpStatusCode.Unauthorized, AuthErrorCodes.UNAUTHORIZED, "Authentication required")
        return null
    }
    if (user.role != role) {
        log.warn("Refused {} for user {}: role is {}, not {}", request.path(), user.id, user.role.sql, role.sql)
        respondApiError(HttpStatusCode.Forbidden, AuthErrorCodes.FORBIDDEN, "This account is not ${role.article()}")
        return null
    }
    return user
}

private fun UserRole.article(): String = when (this) {
    UserRole.ADMIN -> "an administrator"
    UserRole.OWNER -> "the spa owner"
    else -> "a $sql"
}

internal suspend fun ApplicationCall.respondApiError(status: HttpStatusCode, code: String, message: String, field: String? = null) {
    respond(status, ErrorResponse(code, message, field = field))
}
