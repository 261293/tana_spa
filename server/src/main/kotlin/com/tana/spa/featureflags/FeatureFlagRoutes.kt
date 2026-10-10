package com.tana.spa.featureflags

import com.tana.spa.admin.requireAdmin
import com.tana.spa.admin.respondApiError
import com.tana.spa.auth.AuthService
import com.tana.spa.auth.JWT_AUTH
import com.tana.spa.auth.TokenService
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlagAdminResponse
import com.tana.spa.shared.featureflags.FeatureFlagApi
import com.tana.spa.shared.featureflags.FeatureFlagWriteRequest
import com.tana.spa.shared.featureflags.FeatureFlagsResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.tana.spa.featureflags")

/** Install after `authModule`: it owns ContentNegotiation, CORS, StatusPages and the JWT realm. */
fun Application.featureFlagModule(service: FeatureFlagService, tokens: TokenService, auth: AuthService) {
    routing {
        route(FeatureFlagApi.BASE) {
            get {
                call.respond(FeatureFlagsResponse(service.resolveAll(call.callerId(tokens))))
            }

            authenticate(JWT_AUTH) {
                adminRoutes(service, auth)
            }
        }
    }
}

private fun Route.adminRoutes(service: FeatureFlagService, auth: AuthService) {
    // Before "/{key}", so the literal segment is never read as a flag key.
    get("/admin") {
        if (call.requireAdmin(auth) == null) return@get
        call.respondAdminView(service)
    }

    put("/{key}") {
        val admin = call.requireAdmin(auth) ?: return@put
        val flag = call.flagParam() ?: return@put
        service.setGlobal(flag, call.receive<FeatureFlagWriteRequest>().enabled, admin.id)
        call.respondAdminView(service)
    }

    delete("/{key}") {
        val admin = call.requireAdmin(auth) ?: return@delete
        val flag = call.flagParam() ?: return@delete
        respondWrite(service.clearGlobal(flag, admin.id), service, "No global state is stored for this flag")
    }

    put("/{key}/users/{userId}") {
        val admin = call.requireAdmin(auth) ?: return@put
        val flag = call.flagParam() ?: return@put
        val body = call.receive<FeatureFlagWriteRequest>()
        val result = service.setForUser(flag, call.userIdParam(), body.enabled, body.note, admin.id)
        respondWrite(result, service, "No such user")
    }

    delete("/{key}/users/{userId}") {
        val admin = call.requireAdmin(auth) ?: return@delete
        val flag = call.flagParam() ?: return@delete
        val result = service.clearForUser(flag, call.userIdParam(), admin.id)
        respondWrite(result, service, "This user has no override for this flag")
    }
}

private suspend fun RoutingContext.respondWrite(result: FlagWriteResult, service: FeatureFlagService, notStored: String) {
    when (result) {
        FlagWriteResult.Applied -> call.respondAdminView(service)
        else -> call.respondApiError(HttpStatusCode.NotFound, AuthErrorCodes.INVALID_REQUEST, notStored)
    }
}

private suspend fun ApplicationCall.respondAdminView(service: FeatureFlagService) {
    respond(FeatureFlagAdminResponse(service.adminView()))
}

/**
 * Verified here rather than with `authenticate(optional = true)`, which still challenges a token
 * that is present but invalid. This endpoint tells an app which features exist, so a caller whose
 * token has gone stale gets the states that apply to everybody instead of a 401.
 */
private fun ApplicationCall.callerId(tokens: TokenService): String? {
    val header = request.headers[HttpHeaders.Authorization] ?: return null
    val token = header.removePrefix("Bearer ").trim().takeIf { it.isNotEmpty() } ?: return null
    return runCatching { tokens.verifier.verify(token).subject }.getOrNull()
}

private fun ApplicationCall.userIdParam(): String = parameters["userId"].orEmpty()

private suspend fun ApplicationCall.flagParam(): FeatureFlag? {
    val key = parameters["key"].orEmpty()
    val flag = FeatureFlag.fromKey(key)
    if (flag == null) {
        log.info("Unknown feature flag '{}' requested", key)
        respondApiError(HttpStatusCode.NotFound, AuthErrorCodes.UNKNOWN_FLAG, "No such feature flag")
    }
    return flag
}
