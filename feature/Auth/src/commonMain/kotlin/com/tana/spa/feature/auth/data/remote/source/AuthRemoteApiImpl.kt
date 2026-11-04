package com.tana.spa.feature.auth.data.remote.source

import com.tana.spa.core.network.NetworkClient
import com.tana.spa.core.network.withoutTokenRefresh
import com.tana.spa.feature.auth.data.remote.AuthApiException
import com.tana.spa.feature.auth.data.remote.AuthRemoteApi
import com.tana.spa.shared.SupabasePaths
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.AuthValidation
import com.tana.spa.shared.auth.PhoneOtpSendResponse
import com.tana.spa.shared.auth.UserDto
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.cancellation.CancellationException

class AuthRemoteApiImpl(private val networkClient: NetworkClient, private val baseUrl: String) : AuthRemoteApi {

    override suspend fun login(email: String, password: String): AuthResponse = signIn(
        send(HttpMethod.Post, SupabasePaths.PASSWORD_GRANT) {
            json(
                buildJsonObject {
                    put("email", email)
                    put("password", password)
                },
            )
        },
    )

    override suspend fun register(displayName: String, email: String, phone: String, password: String): AuthResponse = signIn(
        send(HttpMethod.Post, SupabasePaths.SIGN_UP, phoneExists = AuthErrorCodes.PHONE_TAKEN) {
            json(
                buildJsonObject {
                    put("email", email)
                    put("password", password)
                    putJsonObject("data") {
                        put("full_name", displayName)
                        put("phone", AuthValidation.normalizePhone(phone) ?: phone)
                    }
                },
            )
        },
    )

    override suspend fun refresh(refreshToken: String): AuthResponse = signIn(
        send(HttpMethod.Post, SupabasePaths.REFRESH_GRANT) { json(buildJsonObject { put("refresh_token", refreshToken) }) },
    )

    override suspend fun logout(accessToken: String) {
        send(HttpMethod.Post, SupabasePaths.LOGOUT) {
            parameter("scope", "local")
            bearerAuth(accessToken)
            withoutTokenRefresh()
        }
    }

    override suspend fun sendPhoneOtp(accessToken: String): PhoneOtpSendResponse {
        val user = me(accessToken)
        if (user.phoneVerified) throw AuthApiException(HTTP_CONFLICT, AuthErrorCodes.PHONE_ALREADY_VERIFIED)
        val phone = phoneOf(user)
        send(HttpMethod.Put, SupabasePaths.USER) {
            bearerAuth(accessToken)
            json(buildJsonObject { put("phone", phone) })
        }
        return PhoneOtpSendResponse(RESEND_AFTER_SECONDS, OTP_LIFETIME_SECONDS, mask(phone))
    }

    override suspend fun verifyPhoneOtp(accessToken: String, code: String): UserDto {
        val phone = phoneOf(me(accessToken))
        send(HttpMethod.Post, SupabasePaths.VERIFY) {
            json(
                buildJsonObject {
                    put("type", "phone_change")
                    put("phone", phone)
                    put("token", code)
                },
            )
        }
        return me(accessToken)
    }

    private suspend fun signIn(response: HttpResponse): AuthResponse {
        val grant = response.body<Grant>()
        return AuthResponse(
            accessToken = grant.accessToken,
            expiresInSeconds = grant.expiresIn,
            user = me(grant.accessToken, refreshable = false),
            refreshToken = grant.refreshToken,
            refreshExpiresInSeconds = REFRESH_LIFETIME_SECONDS,
        )
    }

    private suspend fun me(accessToken: String, refreshable: Boolean = true): UserDto = send(HttpMethod.Post, SupabasePaths.rpc("me")) {
        bearerAuth(accessToken)
        if (!refreshable) withoutTokenRefresh()
    }.body()

    private fun phoneOf(user: UserDto): String =
        user.phone?.let(AuthValidation::normalizePhone) ?: throw AuthApiException(HTTP_BAD_REQUEST, AuthErrorCodes.INVALID_REQUEST)

    private suspend fun send(
        method: HttpMethod,
        path: String,
        phoneExists: String = AuthErrorCodes.PHONE_CLAIMED,
        configure: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val response = networkClient.httpClient.request(baseUrl + path) {
            this.method = method
            configure()
        }
        if (response.status.isSuccess()) return response

        // An unreadable error body (e.g. an HTML page from a proxy) still counts as a server error.
        val error = try {
            response.body<JsonObject>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        throw AuthApiException(
            status = response.status.value,
            code = error?.let { it.text("error_code") ?: it.text("code") }?.let { appCode(it, phoneExists) },
            retryAfterSeconds = error?.number("retryAfterSeconds"),
            attemptsRemaining = error?.number("attemptsRemaining"),
        )
    }

    private inline fun <reified T> HttpRequestBuilder.json(payload: T) {
        contentType(ContentType.Application.Json)
        setBody(payload)
    }

    @Serializable
    private class Grant(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long,
        @SerialName("refresh_token") val refreshToken: String,
    )

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_CONFLICT = 409
        const val RESEND_AFTER_SECONDS = 60
        const val OTP_LIFETIME_SECONDS = 3600
        const val REFRESH_LIFETIME_SECONDS = 30L * 24 * 60 * 60
        const val MASK_VISIBLE = 3
    }

    private fun mask(phone: String): String =
        phone.take(MASK_VISIBLE) + "•".repeat((phone.length - 2 * MASK_VISIBLE).coerceAtLeast(0)) + phone.takeLast(MASK_VISIBLE)
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.number(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull

private fun appCode(authCode: String, phoneExists: String): String = when (authCode) {
    "email_exists", "user_already_exists" -> AuthErrorCodes.EMAIL_TAKEN
    "phone_exists" -> phoneExists
    "over_request_rate_limit", "over_sms_send_rate_limit" -> AuthErrorCodes.TOO_MANY_REQUESTS
    "refresh_token_not_found", "refresh_token_already_used" -> AuthErrorCodes.INVALID_REFRESH_TOKEN
    "sms_send_failed" -> AuthErrorCodes.OTP_UNAVAILABLE
    "phone_provider_disabled" -> AuthErrorCodes.FEATURE_DISABLED
    "weak_password", "validation_failed" -> AuthErrorCodes.INVALID_REQUEST
    else -> authCode
}
