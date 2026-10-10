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

/**
 * Answers the caller and returns null when the request must not proceed.
 *
 * The role is read from the store on every call rather than from a token claim, so revoking
 * someone's admin role takes effect at once instead of when their access token expires.
 */
internal suspend fun ApplicationCall.requireAdmin(auth: AuthService): User? {
    val user = principal<JWTPrincipal>()?.payload?.subject?.let { auth.findUser(it) }
    if (user == null) {
        respondApiError(HttpStatusCode.Unauthorized, AuthErrorCodes.UNAUTHORIZED, "Authentication required")
        return null
    }
    if (user.role != UserRole.ADMIN) {
        log.warn("Refused {} for user {}: role is {}, not admin", request.path(), user.id, user.role.sql)
        respondApiError(HttpStatusCode.Forbidden, AuthErrorCodes.FORBIDDEN, "This account is not an administrator")
        return null
    }
    return user
}

internal suspend fun ApplicationCall.respondApiError(status: HttpStatusCode, code: String, message: String) {
    respond(status, ErrorResponse(code, message))
}
