package com.tana.spa.notifications.push

import com.tana.spa.shared.notifications.PushChannels
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Firebase Cloud Messaging, HTTP v1. One transport for all three clients: Android and the browser
 * receive from FCM directly, and iOS through FCM's APNs relay, so the server never holds an APNs
 * key of its own.
 *
 * Sends one request per token. FCM's v1 API addresses a single token per message, and the number
 * of devices on one account is small; a campaign to many users would want topics instead.
 */
class FcmPushSender(
    private val config: FcmConfig,
    private val tokens: FcmAccessTokens = FcmAccessTokens(config),
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
    private val requestTimeout: Duration = Duration.ofSeconds(10),
) : PushSender {

    override val name = "fcm_http_v1"

    override fun send(tokens: List<String>, message: PushMessage): List<PushOutcome> {
        if (tokens.isEmpty()) return emptyList()
        val bearer = try {
            this.tokens.bearer()
        } catch (e: PushTransportException) {
            // Without a credential nothing can be sent, and every token shares the same fate.
            log.warn("FCM authentication failed: {}", e.message)
            return tokens.map { PushOutcome.Failed(it, e.message ?: "authentication failed", e.retryable) }
        }
        return tokens.map { token -> sendOne(bearer, token, message) }
    }

    private fun sendOne(bearer: String, token: String, message: PushMessage): PushOutcome {
        val response = try {
            post(bearer, body(token, message))
        } catch (e: PushTransportException) {
            log.warn("FCM send to {} failed: {}", tokenHint(token), e.message)
            return PushOutcome.Failed(token, e.message ?: "transport failure", e.retryable)
        }
        if (response.statusCode() in 200..299) return PushOutcome.Delivered(token)

        val status = errorStatus(response.body())
        val outcome = outcomeFor(token, response.statusCode(), status)
        log.warn(
            "FCM refused {} with HTTP {} ({}) -> {}",
            tokenHint(token),
            response.statusCode(),
            status ?: "no status",
            outcome::class.simpleName,
        )
        return outcome
    }

    /**
     * Maps FCM's documented ErrorCode enum. `UNREGISTERED` and `SENDER_ID_MISMATCH` both mean this
     * token will never work again — it was uninstalled, or it belongs to a different Firebase
     * project — so the caller deletes it instead of retrying forever.
     */
    private fun outcomeFor(token: String, httpStatus: Int, errorStatus: String?): PushOutcome = when (errorStatus) {
        "UNREGISTERED", "SENDER_ID_MISMATCH" -> PushOutcome.Unregistered(token)

        "INVALID_ARGUMENT", "PERMISSION_DENIED", "UNAUTHENTICATED" ->
            PushOutcome.Failed(token, errorStatus, retryable = false)

        "QUOTA_EXCEEDED", "UNAVAILABLE", "INTERNAL" -> PushOutcome.Failed(token, errorStatus, retryable = true)

        else -> when (httpStatus) {
            404 -> PushOutcome.Unregistered(token)
            429, in 500..599 -> PushOutcome.Failed(token, "HTTP $httpStatus", retryable = true)
            else -> PushOutcome.Failed(token, "HTTP $httpStatus", retryable = false)
        }
    }

    private fun body(token: String, message: PushMessage): String = buildJsonObject {
        putJsonObject("message") {
            put("token", token)
            putJsonObject("notification") {
                put("title", message.title)
                message.body?.let { put("body", it) }
            }
            if (message.data.isNotEmpty()) {
                putJsonObject("data") { message.data.forEach { (key, value) -> put(key, value) } }
            }
            putJsonObject("android") {
                put("priority", "HIGH")
                // Must match the channel the Android app creates, or Android 8+ drops the post.
                putJsonObject("notification") { put("channel_id", PushChannels.DEFAULT) }
            }
            putJsonObject("apns") {
                putJsonObject("headers") { put("apns-priority", "10") }
                putJsonObject("payload") {
                    putJsonObject("aps") { put("sound", "default") }
                }
            }
        }
    }.toString()

    private fun post(bearer: String, body: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create(config.sendUrl))
            .timeout(requestTimeout)
            .header("Authorization", "Bearer $bearer")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw PushTransportException("Could not reach FCM", cause = e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw PushTransportException("Interrupted while calling FCM", cause = e)
        }
    }

    private fun errorStatus(body: String): String? = runCatching {
        (json.parseToJsonElement(body) as? JsonObject)
            ?.get("error")?.jsonObject
            ?.get("status")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private companion object {
        val log = LoggerFactory.getLogger(FcmPushSender::class.java)!!
        val json = Json { ignoreUnknownKeys = true }
    }
}
