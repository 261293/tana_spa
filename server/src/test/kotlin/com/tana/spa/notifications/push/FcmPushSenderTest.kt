package com.tana.spa.notifications.push

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.tana.spa.shared.notifications.PushChannels
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the sender against a local HTTP server standing in for Google's token endpoint and FCM, so
 * the request shape, the signed assertion and the error mapping are all verified without
 * credentials or network access. (It does not prove FCM's live behaviour, which needs a real
 * Firebase project.)
 */
class FcmPushSenderTest {

    private val sendRequests = CopyOnWriteArrayList<JsonObject>()
    private val bearers = CopyOnWriteArrayList<String?>()
    private val assertions = CopyOnWriteArrayList<String>()
    private val tokenCalls = AtomicInteger()

    private var tokenResponse: () -> Pair<Int, String> = { 200 to """{"access_token":"ya29.test","expires_in":3600}""" }
    private var sendResponse: (JsonObject) -> Pair<Int, String> = { 200 to """{"name":"projects/tana-demo/messages/1"}""" }

    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/token") { ex: HttpExchange ->
            tokenCalls.incrementAndGet()
            val form = ex.requestBody.readBytes().decodeToString().split('&').associate {
                val (k, v) = it.split('=', limit = 2)
                URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
            }
            form["assertion"]?.let { assertions += it }
            respond(ex, tokenResponse())
        }
        createContext("/v1/projects") { ex: HttpExchange ->
            val body = Json.parseToJsonElement(ex.requestBody.readBytes().decodeToString()).jsonObject
            sendRequests += body
            bearers += ex.requestHeaders.getFirst("Authorization")
            respond(ex, sendResponse(body))
        }
        start()
    }

    private fun respond(ex: HttpExchange, response: Pair<Int, String>) {
        val (status, body) = response
        val bytes = body.toByteArray()
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private val baseUrl = "http://127.0.0.1:${server.address.port}"

    private val config = FcmConfig(
        projectId = "tana-demo",
        clientEmail = "push@tana-demo.iam.gserviceaccount.com",
        privateKey = FcmTestKeys.keyPair.private,
        tokenUri = "$baseUrl/token",
        sendBaseUrl = baseUrl,
    )

    private val sender = FcmPushSender(config)

    private val message = PushMessage(
        title = "Đơn hàng đã xác nhận",
        body = "Đơn #1234 đang được chuẩn bị",
        data = mapOf("orderId" to "1234", "type" to "order"),
    )

    @AfterTest
    fun stop() = server.stop(0)

    private fun error(status: String) = """{"error":{"code":404,"message":"x","status":"$status"}}"""

    @Test
    fun theSendUrlCarriesTheProjectFromTheServiceAccount() {
        assertEquals("$baseUrl/v1/projects/tana-demo/messages:send", config.sendUrl)
    }

    @Test
    fun aDeliveredMessageCarriesTheNotificationThePayloadAndTheAndroidChannel() {
        val outcomes = sender.send(listOf("device-token-aaaaaaaaaaaa"), message)

        assertIs<PushOutcome.Delivered>(outcomes.single())
        val sent = sendRequests.single()["message"]!!.jsonObject
        assertEquals("device-token-aaaaaaaaaaaa", sent["token"]!!.jsonPrimitive.content)
        assertEquals("Đơn hàng đã xác nhận", sent["notification"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals("Đơn #1234 đang được chuẩn bị", sent["notification"]!!.jsonObject["body"]!!.jsonPrimitive.content)
        assertEquals("1234", sent["data"]!!.jsonObject["orderId"]!!.jsonPrimitive.content)
        assertEquals("HIGH", sent["android"]!!.jsonObject["priority"]!!.jsonPrimitive.content)
        assertEquals(
            PushChannels.DEFAULT,
            sent["android"]!!.jsonObject["notification"]!!.jsonObject["channel_id"]!!.jsonPrimitive.content,
            "Android 8+ drops a notification whose channel does not exist",
        )
        assertEquals("Bearer ya29.test", bearers.single())
    }

    @Test
    fun aMessageWithoutABodyOmitsItRatherThanSendingNull() {
        sender.send(listOf("device-token-aaaaaaaaaaaa"), PushMessage(title = "Chỉ tiêu đề", body = null))

        val notification = sendRequests.single()["message"]!!.jsonObject["notification"]!!.jsonObject
        assertNull(notification["body"])
        assertNull(sendRequests.single()["message"]!!.jsonObject["data"], "an empty payload is left out")
    }

    @Test
    fun theAssertionIsASignedJwtForTheMessagingScope() {
        sender.send(listOf("device-token-aaaaaaaaaaaa"), message)

        val (header, claims, signature) = assertions.single().split('.').let { Triple(it[0], it[1], it[2]) }
        val verified = Signature.getInstance("SHA256withRSA").run {
            initVerify(FcmTestKeys.keyPair.public)
            update("$header.$claims".toByteArray(Charsets.US_ASCII))
            verify(Base64.getUrlDecoder().decode(signature))
        }
        assertTrue(verified, "FCM would reject an assertion this server cannot verify")

        val decoded = Json.parseToJsonElement(Base64.getUrlDecoder().decode(claims).decodeToString()).jsonObject
        assertEquals("RS256", json(header)["alg"]!!.jsonPrimitive.content)
        assertEquals(config.clientEmail, decoded["iss"]!!.jsonPrimitive.content)
        assertEquals(config.tokenUri, decoded["aud"]!!.jsonPrimitive.content)
        assertEquals("https://www.googleapis.com/auth/firebase.messaging", decoded["scope"]!!.jsonPrimitive.content)
        assertTrue(decoded["exp"]!!.jsonPrimitive.content.toLong() > decoded["iat"]!!.jsonPrimitive.content.toLong())
    }

    private fun json(base64Url: String) = Json.parseToJsonElement(Base64.getUrlDecoder().decode(base64Url).decodeToString()).jsonObject

    @Test
    fun theAccessTokenIsMintedOnceAndReusedForEveryDevice() {
        sender.send(listOf("device-token-aaaaaaaaaaaa", "device-token-bbbbbbbbbbbb"), message)
        sender.send(listOf("device-token-cccccccccccc"), message)

        assertEquals(3, sendRequests.size)
        assertEquals(1, tokenCalls.get(), "minting a token per device would cost a round trip each time")
    }

    @Test
    fun aRejectedKeyFailsEveryTokenAtOnceAndIsNotWorthRetrying() {
        tokenResponse = { 401 to """{"error":"invalid_grant"}""" }

        val outcomes = sender.send(listOf("device-token-aaaaaaaaaaaa", "device-token-bbbbbbbbbbbb"), message)

        assertEquals(2, outcomes.size)
        outcomes.forEach { assertEquals(false, assertIs<PushOutcome.Failed>(it).retryable) }
        assertTrue(sendRequests.isEmpty(), "nothing should be sent without a credential")
    }

    @Test
    fun anUninstalledAppIsReportedSoItsTokenCanBeDeleted() {
        sendResponse = { 404 to error("UNREGISTERED") }

        assertIs<PushOutcome.Unregistered>(sender.send(listOf("device-token-aaaaaaaaaaaa"), message).single())
    }

    @Test
    fun aTokenFromAnotherFirebaseProjectIsAlsoDead() {
        sendResponse = { 403 to error("SENDER_ID_MISMATCH") }

        assertIs<PushOutcome.Unregistered>(sender.send(listOf("device-token-aaaaaaaaaaaa"), message).single())
    }

    @Test
    fun anOutageIsRetryableAndAConfigurationMistakeIsNot() {
        sendResponse = { 503 to error("UNAVAILABLE") }
        assertTrue(assertIs<PushOutcome.Failed>(sender.send(listOf("device-token-aaaaaaaaaaaa"), message).single()).retryable)

        sendResponse = { 429 to error("QUOTA_EXCEEDED") }
        assertTrue(assertIs<PushOutcome.Failed>(sender.send(listOf("device-token-aaaaaaaaaaaa"), message).single()).retryable)

        sendResponse = { 400 to error("INVALID_ARGUMENT") }
        assertEquals(false, assertIs<PushOutcome.Failed>(sender.send(listOf("device-token-aaaaaaaaaaaa"), message).single()).retryable)

        sendResponse = { 403 to error("PERMISSION_DENIED") }
        assertEquals(false, assertIs<PushOutcome.Failed>(sender.send(listOf("device-token-aaaaaaaaaaaa"), message).single()).retryable)
    }

    @Test
    fun anUnreadableErrorBodyFallsBackToTheHttpStatus() {
        sendResponse = { 404 to "<html>not json</html>" }
        assertIs<PushOutcome.Unregistered>(sender.send(listOf("device-token-aaaaaaaaaaaa"), message).single())

        sendResponse = { 500 to "" }
        assertTrue(assertIs<PushOutcome.Failed>(sender.send(listOf("device-token-aaaaaaaaaaaa"), message).single()).retryable)

        sendResponse = { 418 to "" }
        assertEquals(false, assertIs<PushOutcome.Failed>(sender.send(listOf("device-token-aaaaaaaaaaaa"), message).single()).retryable)
    }

    @Test
    fun oneDeadTokenDoesNotStopTheOtherDevices() {
        sendResponse = { body ->
            val token = body["message"]!!.jsonObject["token"]!!.jsonPrimitive.contentOrNull
            if (token == "device-token-bbbbbbbbbbbb") 404 to error("UNREGISTERED") else 200 to "{}"
        }

        val outcomes = sender.send(
            listOf("device-token-aaaaaaaaaaaa", "device-token-bbbbbbbbbbbb", "device-token-cccccccccccc"),
            message,
        )

        assertEquals(2, outcomes.count { it is PushOutcome.Delivered })
        assertEquals("device-token-bbbbbbbbbbbb", outcomes.filterIsInstance<PushOutcome.Unregistered>().single().token)
    }

    @Test
    fun noTokensMeansNoRequestAndNoCredential() {
        assertTrue(sender.send(emptyList(), message).isEmpty())

        assertEquals(0, tokenCalls.get())
        assertTrue(sendRequests.isEmpty())
    }
}
