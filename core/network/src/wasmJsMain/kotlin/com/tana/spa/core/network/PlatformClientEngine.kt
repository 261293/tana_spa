package com.tana.spa.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun platformHttpClient(): HttpClient = HttpClient(Js)
