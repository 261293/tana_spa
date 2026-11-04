package com.tana.spa.core.featureflags

import com.tana.spa.core.network.NetworkClient
import com.tana.spa.core.network.withDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureFlagRemoteApiTest {

    @Test
    fun fetch_posts_to_the_feature_flags_function_with_the_session() = runTest {
        var sent: Triple<HttpMethod, String, String?>? = null
        val engine = MockEngine { request ->
            sent = Triple(request.method, request.url.encodedPath, request.headers[HttpHeaders.Authorization])
            respond(
                """{"flags":{"notifications":true}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = FeatureFlagRemoteApiImpl(NetworkClient(HttpClient(engine).withDefaults()), "http://api.test")

        val flags = api.fetch("a1")

        assertEquals(Triple(HttpMethod.Post, "/rest/v1/rpc/feature_flags", "Bearer a1"), sent)
        assertEquals(mapOf("notifications" to true), flags)
    }
}
