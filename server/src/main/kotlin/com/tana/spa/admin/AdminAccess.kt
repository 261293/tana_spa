package com.tana.spa.admin

import com.tana.spa.auth.AuthService
import com.tana.spa.auth.User
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.SpaPermission
import com.tana.spa.shared.auth.SpaRoles
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.tana.spa.admin")

internal suspend fun ApplicationCall.requireAdmin(auth: AuthService): User? = requirePermission(auth, SpaPermission.OPERATE_SYSTEM)

/**
 * Answers the caller and returns null when the request must not proceed. Every privileged route
 * goes through here, so a route cannot be left unguarded by a copy-paste that drops the check.
 *
 * The role is read from the store on every call rather than from a token claim, so revoking
 * someone's role takes effect at once instead of when their access token expires.
 */
internal suspend fun ApplicationCall.requirePermission(auth: AuthService, permission: SpaPermission): User? {
    val user = principal<JWTPrincipal>()?.payload?.subject?.let { auth.findUser(it) }
    if (user == null) {
        respondApiError(HttpStatusCode.Unauthorized, AuthErrorCodes.UNAUTHORIZED, "Authentication required")
        return null
    }
    if (!SpaRoles.has(user.role.sql, permission)) {
        log.warn("Refused {} for user {}: role {} lacks {}", request.path(), user.id, user.role.sql, permission)
        respondApiError(HttpStatusCode.Forbidden, AuthErrorCodes.FORBIDDEN, permission.refusal())
        return null
    }
    return user
}

private fun SpaPermission.refusal(): String = when (this) {
    SpaPermission.MANAGE_BOOKINGS -> "This account does not work the booking desk"
    SpaPermission.MANAGE_SERVICES -> "This account cannot change the menu"
    SpaPermission.MANAGE_THERAPISTS -> "This account cannot change the therapist roster"
    SpaPermission.OPERATE_SYSTEM -> "This account is not an administrator"
    SpaPermission.MANAGE_ACCOUNTS -> "This account cannot create accounts"
}

internal suspend fun ApplicationCall.respondApiError(status: HttpStatusCode, code: String, message: String, field: String? = null) {
    respond(status, ErrorResponse(code, message, field = field))
}
