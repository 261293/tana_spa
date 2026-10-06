package com.tana.spa.auth.otp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the client against a local HTTP server that speaks Twilio Verify's documented wire format,
 * so request shape, auth and error handling are verified without credentials or network access.
 * (It does not prove Twilio's live behaviour; that needs a real service SID.)
 */
class TwilioVerifyProviderTest {

    private class Recorded(val method: String, val path: String, val authorization: String?, val form: Map<String, String>)

    private val requests = CopyOnWriteArrayList<Recorded>()
    private var responder: (Recorded) -> Pair<Int, String> = { 200 to "{}" }

    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { ex: HttpExchange ->
            val body = ex.requestBody.readBytes().decodeToString()
            val form = body.split('&').filter { it.isNotEmpty() }.associate {
                val (k, v) = it.split('=', limit = 2)
                java.net.URLDecoder.decode(k, Charsets.UTF_8) to java.net.URLDecoder.decode(v, Charsets.UTF_8)
            }
            val rec = Recorded(ex.requestMethod, ex.requestURI.path, ex.requestHeaders.getFirst("Authorization"), form)
            requests += rec
            val (status, json) = responder(rec)
            val bytes = json.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        start()
    }

    private val provider = TwilioVerifyProvider(
        apiKeySid = "SKtest",
        apiKeySecret = "s3cret",
        serviceSid = "VA123",
        baseUrl = "http://127.0.0.1:${server.address.port}",
    )

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun startPostsAnAuthenticatedFormToTheVerificationsEndpoint() {
        responder = { 201 to """{"sid":"VE999","status":"pending"}""" }

        val ref = provider.start("+84333433969", "vi")

        assertEquals("VE999", ref)
        val req = requests.single()
        assertEquals("POST", req.method)
        assertEquals("/v2/Services/VA123/Verifications", req.path)
        assertEquals("Basic " + Base64.getEncoder().encodeToString("SKtest:s3cret".toByteArray()), req.authorization)
        // The plus sign must survive URL-encoding: "+84…" is how Twilio identifies the country.
        assertEquals(mapOf("To" to "+84333433969", "Channel" to "sms", "Locale" to "vi"), req.form)
    }

    @Test
    fun noLocaleMeansNoLocaleParameter() {
        responder = { 201 to """{"sid":"VE1"}""" }
        provider.start("+16502530000", null)
        assertFalse("Locale" in requests.single().form)
    }

    @Test
    fun anUnsupportedLocaleIsRetriedWithoutIt() {
        responder = { req -> if ("Locale" in req.form) 404 to "{}" else 201 to """{"sid":"VE2"}""" }

        assertEquals("VE2", provider.start("+84333433969", "xx"))

        assertEquals(2, requests.size)
        assertTrue("Locale" in requests[0].form)
        assertFalse("Locale" in requests[1].form)
    }

    @Test
    fun aMissingServiceIsAnErrorNotARetryLoop() {
        responder = { 404 to "{}" }
        assertFailsWith<ProviderException> { provider.start("+84333433969", null) }
        assertEquals(1, requests.size)
    }

    @Test
    fun sendFailuresSurfaceAsProviderExceptionsWithoutLeakingTheBody() {
        responder = { 500 to """{"message":"boom for +84333433969"}""" }
        val e = assertFailsWith<ProviderException> { provider.start("+84333433969", null) }
        assertFalse(e.rateLimited)
        assertFalse("+8433" in e.message.orEmpty(), "error text must not echo the phone number")
    }

    @Test
    fun rateLimitsAreFlagged() {
        responder = { 429 to "{}" }
        assertTrue(assertFailsWith<ProviderException> { provider.start("+84333433969", null) }.rateLimited)
        assertTrue(assertFailsWith<ProviderException> { provider.check("+84333433969", "123456") }.rateLimited)
    }

    @Test
    fun checkIsTrueOnlyForAnApprovedStatus() {
        responder = { 200 to """{"sid":"VE1","status":"approved","valid":true}""" }
        assertTrue(provider.check("+84333433969", "123456"))
        val req = requests.single()
        assertEquals("/v2/Services/VA123/VerificationCheck", req.path)
        assertEquals(mapOf("To" to "+84333433969", "Code" to "123456"), req.form)

        responder = { 200 to """{"status":"pending","valid":false}""" }
        assertFalse(provider.check("+84333433969", "000000"))
    }

    @Test
    fun anExpiredUsedOrMalformedCodeIsFalseNotAnOutage() {
        responder = { 404 to "{}" }
        assertFalse(provider.check("+84333433969", "123456"))
        responder = { 400 to "{}" }
        assertFalse(provider.check("+84333433969", "abc"))
    }

    @Test
    fun serverErrorsDuringCheckAreOutages() {
        responder = { 503 to "{}" }
        assertFailsWith<ProviderException> { provider.check("+84333433969", "123456") }
    }

    @Test
    fun anUnreadableSuccessBodyIsAnOutage() {
        responder = { 200 to "<html>not json</html>" }
        assertFailsWith<ProviderException> { provider.check("+84333433969", "123456") }
        assertNull(runCatching { provider.start("+84333433969", null) }.getOrNull())
    }

    @Test
    fun anUnreachableServerIsAnOutage() {
        val dead = TwilioVerifyProvider("SK", "x", "VA", baseUrl = "http://127.0.0.1:1")
        assertFailsWith<ProviderException> { dead.start("+84333433969", null) }
        assertFailsWith<ProviderException> { dead.check("+84333433969", "123456") }
    }
}
