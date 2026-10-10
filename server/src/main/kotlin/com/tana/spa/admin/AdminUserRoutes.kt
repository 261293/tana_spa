package com.tana.spa.admin

import com.tana.spa.auth.AuthService
import com.tana.spa.auth.JWT_AUTH
import com.tana.spa.auth.User
import com.tana.spa.shared.admin.AdminApi
import com.tana.spa.shared.admin.AdminUserDto
import com.tana.spa.shared.admin.AdminUsersResponse
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

private const val DEFAULT_USER_RESULTS = 20

/**
 * The portal page is public because it is only HTML and JavaScript: it holds no data and every
 * call it makes is authorised server-side.
 *
 * Install after `authModule`, which owns the plugins and the JWT realm.
 */
fun Application.adminModule(auth: AuthService) {
    routing {
        staticResources("/admin", "admin")

        authenticate(JWT_AUTH) {
            route(AdminApi.USERS) {
                get {
                    if (call.requireAdmin(auth) == null) return@get
                    call.respond(AdminUsersResponse(call.searchUsers(auth).map(User::toAdminDto)))
                }
            }
        }
    }
}

private suspend fun ApplicationCall.searchUsers(auth: AuthService): List<User> {
    val query = request.queryParameters["q"].orEmpty()
    if (query.isBlank()) return emptyList()
    return auth.searchUsers(query, searchLimit())
}

private fun ApplicationCall.searchLimit(): Int = request.queryParameters["limit"]?.toIntOrNull()
    ?.coerceIn(1, AdminApi.MAX_USER_RESULTS)
    ?: DEFAULT_USER_RESULTS

private fun User.toAdminDto() = AdminUserDto(id = id, displayName = displayName, email = email, role = role.sql)
