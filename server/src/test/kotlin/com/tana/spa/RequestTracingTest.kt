package com.tana.spa

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.tana.spa.auth.AuthConfig
import com.tana.spa.auth.AuthServices
import com.tana.spa.auth.PasswordHasher
import com.tana.spa.auth.REQUEST_ID_HEADER
import com.tana.spa.auth.SESSION_ID_HEADER
import com.tana.spa.shared.auth.AuthApi
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RequestTracingTest {
    private val authLogger = LoggerFactory.getLogger("com.tana.spa.auth") as Logger
    private val captured = ListAppender<ILoggingEvent>()

    @BeforeTest
    fun attach() {
        captured.start()
        authLogger.addAppender(captured)
    }

    @AfterTest
    fun detach() {
        authLogger.detachAppender(captured)
    }

    private val services = AuthServices.create(
        config = AuthConfig(jwtSecret = "x".repeat(40)),
        hasher = object : PasswordHasher {
            override fun hash(password: String) = "fake:$password"
            override fun verify(password: String, stored: String) = stored == "fake:$password"
        },
    )

    private suspend fun io.ktor.client.HttpClient.login() = post(AuthApi.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"nobody@example.com","password":"password123"}""")
    }

    @Test
    fun clientRequestIdIsEchoedAndAppearsInLogContext() = testApplication {
        application { module(services) }
        val response = client.post(AuthApi.LOGIN) {
            contentType(ContentType.Application.Json)
            header(REQUEST_ID_HEADER, "abc123def456")
            header(SESSION_ID_HEADER, "sess0001")
            setBody("""{"email":"nobody@example.com","password":"password123"}""")
        }
        assertEquals("abc123def456", response.headers[REQUEST_ID_HEADER])
        val event = captured.list.first { it.formattedMessage.startsWith("login ->") }
        assertEquals("abc123def456", event.mdcPropertyMap["requestId"])
        assertEquals("sess0001", event.mdcPropertyMap["sessionId"])
    }

    @Test
    fun missingRequestIdIsGeneratedAndLogged() = testApplication {
        application { module(services) }
        val response = client.login()
        val id = response.headers[REQUEST_ID_HEADER]
        assertTrue(!id.isNullOrBlank(), "server should generate an id")
        val event = captured.list.first { it.formattedMessage.startsWith("login ->") }
        assertEquals(id, event.mdcPropertyMap["requestId"])
    }

    @Test
    fun unsafeRequestIdIsReplaced() = testApplication {
        application { module(services) }
        // Spaces and punctuation could be used to make one request look like several log lines.
        val hostile = "x y FAKE LOG LINE"
        val response = client.post(AuthApi.LOGIN) {
            contentType(ContentType.Application.Json)
            header(REQUEST_ID_HEADER, hostile)
            setBody("""{"email":"nobody@example.com","password":"password123"}""")
        }
        val id = response.headers[REQUEST_ID_HEADER]
        assertNotEquals(hostile, id)
        assertTrue(!id.isNullOrBlank(), "an unsafe id must be replaced by a generated one")
        val event = captured.list.first { it.formattedMessage.startsWith("login ->") }
        assertEquals(id, event.mdcPropertyMap["requestId"])
    }

    @Test
    fun malformedBodyLogDoesNotQuoteThePayload() = testApplication {
        application { module(services) }
        client.post(AuthApi.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"a@b.co","password":"SuperSecret!1" """)
        }
        val text = captured.list.joinToString("\n") { it.formattedMessage }
        assertTrue("SuperSecret" !in text, "request body leaked into logs: $text")
    }
}
