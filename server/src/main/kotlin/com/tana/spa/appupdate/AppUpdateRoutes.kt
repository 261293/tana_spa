package com.tana.spa.appupdate

import com.tana.spa.admin.requireAdmin
import com.tana.spa.admin.respondApiError
import com.tana.spa.auth.AuthService
import com.tana.spa.auth.JWT_AUTH
import com.tana.spa.auth.refuseIfDisabled
import com.tana.spa.featureflags.FeatureFlagService
import com.tana.spa.shared.appupdate.AppUpdateApi
import com.tana.spa.shared.appupdate.AppUpdateConfigWriteRequest
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.featureflags.FeatureFlag
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/** Install after `authModule`: it owns ContentNegotiation, CORS, StatusPages and the JWT realm. */
fun Application.appUpdateModule(service: AppUpdateService, flags: FeatureFlagService, auth: AuthService) {
    routing {
        route(AppUpdateApi.BASE) {
            // Unauthenticated on purpose: the gate decides before anyone can sign in, and a
            // 204 tells the client there is no gate rather than failing its parse.
            get {
                // Anonymous, so a per-user override cannot apply here; the kill switch is global.
                if (call.refuseIfDisabled(flags, FeatureFlag.APP_UPDATE_GATE, userId = null)) return@get
                val config = service.config()
                if (config == null) call.respond(HttpStatusCode.NoContent) else call.respond(config)
            }

            authenticate(JWT_AUTH) {
                put {
                    val admin = call.requireAdmin(auth) ?: return@put
                    when (val result = service.save(call.receive<AppUpdateConfigWriteRequest>(), admin.id)) {
                        AppUpdateWriteResult.Applied ->
                            service.config()?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NoContent)

                        is AppUpdateWriteResult.InvalidVersion -> call.respondApiError(
                            HttpStatusCode.BadRequest,
                            AuthErrorCodes.INVALID_REQUEST,
                            "${result.field} must look like 1.2.3",
                        )

                        AppUpdateWriteResult.MinimumAboveLatest -> call.respondApiError(
                            HttpStatusCode.BadRequest,
                            AuthErrorCodes.INVALID_REQUEST,
                            "minimumVersion cannot be newer than latestVersion",
                        )
                    }
                }
            }
        }
    }
}
