package com.tana.spa.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Provides a platform-configured Ktor [HttpClient] (engine differs per target). */
expect fun platformHttpClient(): HttpClient

/**
 * Behaviour every engine gets: JSON bodies and a request timeout.
 * Only the request timeout is set because the browser (JS) engine supports no other kind.
 */
fun HttpClient.withDefaults(): HttpClient = config {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(RequestTracing)
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
    }
}

/** Thin wrapper around the shared [HttpClient] used by feature remote sources. */
class NetworkClient(val httpClient: HttpClient = platformHttpClient().withDefaults())
