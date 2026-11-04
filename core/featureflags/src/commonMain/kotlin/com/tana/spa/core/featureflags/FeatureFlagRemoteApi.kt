package com.tana.spa.core.featureflags

import com.tana.spa.core.network.NetworkClient
import com.tana.spa.shared.SupabasePaths
import com.tana.spa.shared.featureflags.FeatureFlagsResponse
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.http.isSuccess

interface FeatureFlagRemoteApi {
    /** A null [accessToken] asks for the states that apply to everybody. Throws on any failure. */
    suspend fun fetch(accessToken: String?): Map<String, Boolean>
}

class FeatureFlagRemoteApiImpl(private val networkClient: NetworkClient, private val baseUrl: String) : FeatureFlagRemoteApi {

    override suspend fun fetch(accessToken: String?): Map<String, Boolean> {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("feature_flags")) {
            accessToken?.let { bearerAuth(it) }
        }
        check(response.status.isSuccess()) { "Feature flags request failed with ${response.status.value}" }
        return response.body<FeatureFlagsResponse>().flags
    }
}
