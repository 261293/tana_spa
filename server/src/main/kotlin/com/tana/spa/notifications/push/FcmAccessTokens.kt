package com.tana.spa.notifications.push

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.Signature
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** The transport could not complete the call. [retryable] is false for a configuration mistake. */
class PushTransportException(message: String, val retryable: Boolean = true, cause: Throwable? = null) : Exception(message, cause)

/**
 * Mints the OAuth2 access token FCM HTTP v1 expects, with the "self-signed JWT" flow Google
 * documents for service accounts: sign a short-lived assertion with the account's private key and
 * exchange it at the token endpoint.
 *
 * Done here rather than with the Google auth library because the server needs exactly this one
 * grant, and that library would pull in the whole google-api-client stack.
 *
 * The token is cached until shortly before it expires; minting one costs a network round trip and
 * an RSA signature, and FCM is called once per device.
 */
class FcmAccessTokens(
    private val config: FcmConfig,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
    private val requestTimeout: Duration = Duration.ofSeconds(10),
    private val now: () -> Instant = Instant::now,
) {
    private var cachedToken: String? = null
    private var expiresAt: Instant = Instant.EPOCH

    @Synchronized
    fun bearer(): String {
        cachedToken?.let { if (now().isBefore(expiresAt)) return it }
        val response = exchange(assertion())
        val token = response["access_token"]?.jsonPrimitive?.contentOrNull
            ?: throw PushTransportException("Google token endpoint returned no access_token", retryable = false)
        val lifetime = response["expires_in"]?.jsonPrimitive?.intOrNull ?: DEFAULT_LIFETIME_SECONDS
        cachedToken = token
        // Renewed early so a token cannot expire between this check and the FCM call.
        expiresAt = now().plusSeconds((lifetime - EARLY_RENEWAL_SECONDS).coerceAtLeast(1).toLong())
        return token
    }

    private fun assertion(): String {
        val issuedAt = now().epochSecond
        val header = buildJsonObject {
            put("alg", "RS256")
            put("typ", "JWT")
        }
        val claims = buildJsonObject {
            put("iss", config.clientEmail)
            put("scope", MESSAGING_SCOPE)
            put("aud", config.tokenUri)
            put("iat", issuedAt)
            put("exp", issuedAt + ASSERTION_LIFETIME_SECONDS)
        }
        val signingInput = base64Url(header.toString().toByteArray(StandardCharsets.UTF_8)) +
            "." + base64Url(claims.toString().toByteArray(StandardCharsets.UTF_8))
        val signature = try {
            Signature.getInstance("SHA256withRSA").run {
                initSign(config.privateKey)
                update(signingInput.toByteArray(StandardCharsets.US_ASCII))
                sign()
            }
        } catch (e: Exception) {
            throw PushTransportException("Could not sign the FCM assertion (${e::class.simpleName})", retryable = false, cause = e)
        }
        return signingInput + "." + base64Url(signature)
    }

    private fun exchange(assertion: String): kotlinx.serialization.json.JsonObject {
        val form = "grant_type=" + enc(JWT_BEARER_GRANT) + "&assertion=" + enc(assertion)
        val request = HttpRequest.newBuilder(URI.create(config.tokenUri))
            .timeout(requestTimeout)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw PushTransportException("Could not reach the Google token endpoint", cause = e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw PushTransportException("Interrupted while minting an FCM token", cause = e)
        }
        // 400/401 here means the key, the clock or the project is wrong: retrying changes nothing.
        if (response.statusCode() !in 200..299) {
            throw PushTransportException(
                "Google token endpoint answered HTTP ${response.statusCode()}",
                retryable = response.statusCode() >= 500 || response.statusCode() == 429,
            )
        }
        return try {
            json.parseToJsonElement(response.body()).jsonObject
        } catch (e: Exception) {
            throw PushTransportException("Google token endpoint returned an unreadable body", cause = e)
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        const val MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
        const val JWT_BEARER_GRANT = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val ASSERTION_LIFETIME_SECONDS = 3600L
        const val DEFAULT_LIFETIME_SECONDS = 3600
        const val EARLY_RENEWAL_SECONDS = 60

        val json = Json { ignoreUnknownKeys = true }
    }
}
