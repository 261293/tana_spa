package com.tana.spa.feature.auth.data.remote.source

import com.tana.spa.core.network.NetworkClient
import com.tana.spa.feature.auth.data.remote.AuthApiException
import com.tana.spa.feature.auth.data.remote.AuthRemoteApi
import com.tana.spa.shared.auth.AuthApi
import com.tana.spa.shared.auth.AuthResponse
import com.tana.spa.shared.auth.ErrorResponse
import com.tana.spa.shared.auth.LoginRequest
import com.tana.spa.shared.auth.PhoneOtpSendResponse
import com.tana.spa.shared.auth.PhoneOtpVerifyRequest
import com.tana.spa.shared.auth.RegisterRequest
import com.tana.spa.shared.auth.UserDto
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class AuthRemoteApiImpl(
    private val networkClient: NetworkClient,
    private val baseUrl: String,
) : AuthRemoteApi {

    override suspend fun login(email: String, password: String): AuthResponse =
        call(AuthApi.LOGIN) { json(LoginRequest(email, password)) }.body()

    override suspend fun register(displayName: String, email: String, phone: String, password: String): AuthResponse =
        call(AuthApi.REGISTER) { json(RegisterRequest(displayName, email, phone, password)) }.body()

    override suspend fun sendPhoneOtp(accessToken: String): PhoneOtpSendResponse =
        call(AuthApi.PHONE_OTP_SEND) { bearerAuth(accessToken) }.body()

    override suspend fun verifyPhoneOtp(accessToken: String, code: String): UserDto =
        call(AuthApi.PHONE_OTP_VERIFY) {
            bearerAuth(accessToken)
            json(PhoneOtpVerifyRequest(code))
        }.body()

    private suspend fun call(path: String, configure: HttpRequestBuilder.() -> Unit): HttpResponse {
        val response = networkClient.httpClient.post(baseUrl + path, configure)
        if (response.status.isSuccess()) return response

        // An unreadable error body (e.g. an HTML page from a proxy) still counts as a server error.
        val error = try {
            response.body<ErrorResponse>()
        } catch (_: Exception) {
            null
        }
        throw AuthApiException(
            status = response.status.value,
            code = error?.code,
            retryAfterSeconds = error?.retryAfterSeconds,
            attemptsRemaining = error?.attemptsRemaining,
        )
    }

    private inline fun <reified T> HttpRequestBuilder.json(payload: T) {
        contentType(ContentType.Application.Json)
        setBody(payload)
    }
}
