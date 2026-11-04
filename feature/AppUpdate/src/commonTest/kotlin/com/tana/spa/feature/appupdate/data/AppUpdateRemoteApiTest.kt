package com.tana.spa.feature.appupdate.data

import com.tana.spa.core.network.NetworkClient
import com.tana.spa.core.network.withDefaults
import com.tana.spa.shared.appupdate.AppUpdateConfigResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppUpdateRemoteApiTest {

    private val sent = mutableListOf<String>()

    private fun api(status: HttpStatusCode, body: String): AppUpdateRemoteApi {
        val engine = MockEngine { request ->
            sent += "${request.method.value} ${request.url.encodedPath}"
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return AppUpdateRemoteApiImpl(NetworkClient(HttpClient(engine).withDefaults()), "http://api.test")
    }

    @Test
    fun fetch_posts_to_the_app_update_config_function() = runTest {
        val config = api(HttpStatusCode.OK, """{"minimumVersion":"1.0.0","latestVersion":"1.2.0","forceUpdate":false}""").fetch()

        assertEquals(listOf("POST /rest/v1/rpc/app_update_config"), sent)
        assertEquals(AppUpdateConfigResponse("1.0.0", "1.2.0", false), config)
    }

    @Test
    fun a_null_body_means_no_gate() = runTest {
        assertNull(api(HttpStatusCode.OK, "null").fetch())
    }

    @Test
    fun a_disabled_gate_is_a_failure() = runTest {
        assertFailsWith<IllegalStateException> {
            api(HttpStatusCode.ServiceUnavailable, """{"code":"feature_disabled","message":"off"}""").fetch()
        }
    }
}
