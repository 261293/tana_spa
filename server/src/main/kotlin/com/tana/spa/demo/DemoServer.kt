package com.tana.spa.demo

import com.tana.spa.shared.sayHello
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Demo routes for the server platform.
 *
 * Register these from [Application.module] with:
 * ```
 * demoRoutes()
 * ```
 */
fun Application.demoRoutes() {
    routing {
        get("/demo") {
            call.respondText(sayHello("Server Demo"))
        }
        get("/demo/{name}") {
            val name = call.parameters["name"] ?: "World"
            call.respondText(sayHello(name))
        }
    }
}
