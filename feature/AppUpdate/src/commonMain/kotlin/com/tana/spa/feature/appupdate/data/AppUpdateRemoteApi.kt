package com.tana.spa.feature.appupdate.data

import com.tana.spa.core.network.NetworkClient
import com.tana.spa.shared.appupdate.AppUpdateApi
import com.tana.spa.shared.appupdate.AppUpdateConfigResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

interface AppUpdateRemoteApi {
    /** Null when the server says there is no gate. Throws on any failure. */
    suspend fun fetch(): AppUpdateConfigResponse?
}

class AppUpdateRemoteApiImpl(private val networkClient: NetworkClient, private val baseUrl: String) : AppUpdateRemoteApi {

    override suspend fun fetch(): AppUpdateConfigResponse? {
        val response = networkClient.httpClient.get(baseUrl + AppUpdateApi.CONFIG)
        if (response.status == HttpStatusCode.NoContent) return null
        check(response.status.isSuccess()) { "App update request failed with ${response.status.value}" }
        return response.body()
    }
}
