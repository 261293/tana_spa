package com.tana.spa.admin

import com.tana.spa.auth.AuthResult
import com.tana.spa.auth.AuthService
import com.tana.spa.auth.JWT_AUTH
import com.tana.spa.auth.User
import com.tana.spa.auth.UserRole
import com.tana.spa.shared.auth.AccountErrorCodes
import com.tana.spa.shared.auth.AccountsApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.ChangeRoleRequest
import com.tana.spa.shared.auth.CreateAccountRequest
import com.tana.spa.shared.auth.CreatedAccountDto
import com.tana.spa.shared.auth.SpaPermission
import com.tana.spa.shared.auth.SpaRoles
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.tana.spa.accounts")

fun Application.accountsModule(auth: AuthService) {
    routing {
        authenticate(JWT_AUTH) {
            route(AccountsApi.BASE) {
                post {
                    val caller = call.requirePermission(auth, SpaPermission.MANAGE_ACCOUNTS) ?: return@post
                    val request = call.receive<CreateAccountRequest>()

                    if (!SpaRoles.canCreate(caller.role.sql, request.role)) {
                        log.warn("Refused: {} tried to create a {}", caller.role.sql, request.role)
                        return@post call.respondApiError(
                            HttpStatusCode.Forbidden,
                            AccountErrorCodes.ROLE_NOT_ALLOWED,
                            "This account cannot create a ${request.role}",
                            field = "role",
                        )
                    }

                    val role = UserRole.entries.firstOrNull { it.sql == request.role }
                        ?: return@post call.respondApiError(
                            HttpStatusCode.BadRequest,
                            AuthErrorCodes.INVALID_REQUEST,
                            "Unknown role",
                            field = "role",
                        )

                    when (val result = auth.createAccount(request.displayName, request.email, request.password, role)) {
                        is AuthResult.Success -> {
                            log.info("{} created {} ({})", caller.role.sql, result.user.email, role.sql)
                            call.respond(HttpStatusCode.Created, result.user.toCreatedDto())
                        }

                        AuthResult.EmailTaken -> call.respondApiError(
                            HttpStatusCode.Conflict,
                            AuthErrorCodes.EMAIL_TAKEN,
                            "That address already has an account",
                            field = "email",
                        )

                        else -> call.respondApiError(
                            HttpStatusCode.BadRequest,
                            AuthErrorCodes.INVALID_REQUEST,
                            "Those details are not valid",
                        )
                    }
                }

                patch("/{id}/role") {
                    val caller = call.requirePermission(auth, SpaPermission.MANAGE_ACCOUNTS) ?: return@patch
                    val targetId = call.parameters["id"].orEmpty()
                    val request = call.receive<ChangeRoleRequest>()

                    if (targetId == caller.id) {
                        return@patch call.respondApiError(
                            HttpStatusCode.Forbidden,
                            AccountErrorCodes.OWN_ROLE_UNCHANGEABLE,
                            "An account cannot change its own role",
                        )
                    }

                    val target = auth.findUser(targetId)
                        ?: return@patch call.respondApiError(
                            HttpStatusCode.NotFound,
                            AccountErrorCodes.UNKNOWN_ACCOUNT,
                            "No such account",
                        )

                    if (!SpaRoles.canChangeRole(caller.role.sql, target.role.sql, request.role)) {
                        log.warn(
                            "Refused: {} tried to change a {} into a {}",
                            caller.role.sql,
                            target.role.sql,
                            request.role,
                        )
                        return@patch call.respondApiError(
                            HttpStatusCode.Forbidden,
                            AccountErrorCodes.ROLE_NOT_ALLOWED,
                            "This account cannot make that change",
                            field = "role",
                        )
                    }

                    val role = UserRole.entries.firstOrNull { it.sql == request.role }
                        ?: return@patch call.respondApiError(
                            HttpStatusCode.BadRequest,
                            AuthErrorCodes.INVALID_REQUEST,
                            "Unknown role",
                            field = "role",
                        )

                    if (!auth.changeRole(targetId, role)) {
                        return@patch call.respondApiError(
                            HttpStatusCode.NotFound,
                            AccountErrorCodes.UNKNOWN_ACCOUNT,
                            "No such account",
                        )
                    }
                    log.info("{} changed {} from {} to {}", caller.role.sql, target.id, target.role.sql, role.sql)
                    call.respond(HttpStatusCode.OK, target.copy(role = role).toCreatedDto())
                }
            }
        }
    }
}

private fun User.toCreatedDto() = CreatedAccountDto(id = id, displayName = displayName, email = email, role = role.sql)
