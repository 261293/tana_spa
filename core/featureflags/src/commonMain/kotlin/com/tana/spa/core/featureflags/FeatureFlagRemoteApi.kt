package com.tana.spa.core.featureflags

import com.tana.spa.core.network.NetworkClient
import com.tana.spa.shared.featureflags.FeatureFlagApi
import com.tana.spa.shared.featureflags.FeatureFlagsResponse
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.isSuccess

interface FeatureFlagRemoteApi {
    /** A null [accessToken] asks for the states that apply to everybody. Throws on any failure. */
    suspend fun fetch(accessToken: String?): Map<String, Boolean>
}

class FeatureFlagRemoteApiImpl(private val networkClient: NetworkClient, private val baseUrl: String) : FeatureFlagRemoteApi {

    override suspend fun fetch(accessToken: String?): Map<String, Boolean> {
        val response = networkClient.httpClient.get(baseUrl + FeatureFlagApi.RESOLVED) {
            accessToken?.let { bearerAuth(it) }
        }
        check(response.status.isSuccess()) { "Feature flags request failed with ${response.status.value}" }
        return response.body<FeatureFlagsResponse>().flags
    }
}

/** The session lives in `feature:Auth`, which `core` cannot depend on; `composeApp` binds this. */
fun interface AccessTokenSource {
    fun accessToken(): String?

    companion object {
        val none: AccessTokenSource = AccessTokenSource { null }
    }
}
