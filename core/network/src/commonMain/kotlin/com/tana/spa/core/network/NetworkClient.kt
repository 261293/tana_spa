package com.tana.spa.core.network

import io.ktor.client.HttpClient

/** Provides a platform-configured Ktor [HttpClient] (engine differs per target). */
expect fun platformHttpClient(): HttpClient

/** Thin wrapper around the shared [HttpClient] used by feature remote sources. */
class NetworkClient(val httpClient: HttpClient = platformHttpClient())
