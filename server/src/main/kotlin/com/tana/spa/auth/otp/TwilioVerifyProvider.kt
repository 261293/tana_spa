package com.tana.spa.auth.otp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64

/**
 * Twilio Verify (https://www.twilio.com/docs/verify/api): Twilio generates, sends and checks the
 * code, covers 180+ countries, and ships SMS-fraud protection ("Fraud Guard") on by default.
 *
 * Authenticates with a scoped API key, never the account auth token. Error bodies are not logged
 * or surfaced because they echo the phone number.
 *
 * @param baseUrl overridable so tests can point it at a local fake.
 */
class TwilioVerifyProvider(
    apiKeySid: String,
    apiKeySecret: String,
    private val serviceSid: String,
    private val baseUrl: String = "https://verify.twilio.com",
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
    private val requestTimeout: Duration = Duration.ofSeconds(10),
) : PhoneVerificationProvider {

    override val name = "twilio_verify"

    private val authorization = "Basic " +
        Base64.getEncoder().encodeToString("$apiKeySid:$apiKeySecret".toByteArray(StandardCharsets.UTF_8))

    override fun start(phoneE164: String, locale: String?): String? {
        var response = post("Verifications", buildList {
            add("To" to phoneE164)
            add("Channel" to "sms")
            if (locale != null) add("Locale" to locale)
        })
        // Twilio answers 404 for a locale it has no template for and recommends retrying without
        // it, which falls back to the destination country's default language (or English).
        if (response.statusCode() == 404 && locale != null) {
            response = post("Verifications", listOf("To" to phoneE164, "Channel" to "sms"))
        }
        return when (response.statusCode()) {
            200, 201 -> json(response)["sid"]?.jsonPrimitive?.contentOrNull
            429 -> throw ProviderException("Twilio Verify rate limited the send", rateLimited = true)
            else -> throw ProviderException("Twilio Verify send failed with HTTP ${response.statusCode()}")
        }
    }

    override fun check(phoneE164: String, code: String): Boolean {
        val response = post("VerificationCheck", listOf("To" to phoneE164, "Code" to code))
        return when (response.statusCode()) {
            200, 201 -> json(response)["status"]?.jsonPrimitive?.contentOrNull == "approved"
            // 404: the verification expired, was already approved, or exhausted its attempts.
            // 400: malformed code. Neither is an outage; the code is simply not valid.
            400, 404 -> false
            429 -> throw ProviderException("Twilio Verify rate limited the check", rateLimited = true)
            else -> throw ProviderException("Twilio Verify check failed with HTTP ${response.statusCode()}")
        }
    }

    private fun post(resource: String, form: List<Pair<String, String>>): HttpResponse<String> {
        val body = form.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/v2/Services/$serviceSid/$resource"))
            .timeout(requestTimeout)
            .header("Authorization", authorization)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw ProviderException("Could not reach Twilio Verify", cause = e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ProviderException("Interrupted while calling Twilio Verify", cause = e)
        }
    }

    private fun json(response: HttpResponse<String>): JsonObject = try {
        Json.parseToJsonElement(response.body()).jsonObject
    } catch (e: Exception) {
        throw ProviderException("Twilio Verify returned an unreadable response", cause = e)
    }

    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
