package com.tana.spa.core.network

import com.tana.spa.core.logging.AppLog
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey

private const val BEARER = "Bearer "
private val AlreadyRetried = AttributeKey<Boolean>("SpaTokenRefreshRetried")

class TokenRefreshConfig {
    /**
     * A provider rather than the refresher itself: resolving it eagerly would ask the container
     * for the session while it is still building the client the session's own calls go through.
     */
    var refresher: () -> SessionRefresher = { SessionRefresher.none }
}

/**
 * Turns one 401 into a token refresh and a single retry, so an access token reaching the end of
 * its hour costs a round trip instead of a trip back to the sign-in screen.
 *
 * Only calls that carried a bearer token are retried: a 401 from sign-in means the password was
 * wrong, and the refresh grant carries no token, which is also what stops this plugin recursing
 * into itself. Calls that carry a token but must not be retried opt out with [withoutTokenRefresh].
 */
val TokenRefresh = createClientPlugin("TokenRefresh", ::TokenRefreshConfig) {
    val provideRefresher = pluginConfig.refresher

    on(Send) { request ->
        val call = proceed(request)
        if (call.response.status != HttpStatusCode.Unauthorized) return@on call
        if (request.attributes.contains(AlreadyRetried)) return@on call
        val stale = request.bearerToken() ?: return@on call

        val fresh = provideRefresher().refresh(stale)
        if (fresh == null) {
            AppLog.i(TAG) { "401 on ${request.method.value} ${request.url.build().encodedPath}: no fresh token, giving up" }
            return@on call
        }
        AppLog.i(TAG) { "401 on ${request.method.value} ${request.url.build().encodedPath}: refreshed, retrying once" }
        request.attributes.put(AlreadyRetried, true)
        request.headers[HttpHeaders.Authorization] = BEARER + fresh
        proceed(request)
    }
}

/**
 * For a call whose token must not be swapped for the stored session's: the first call made with a
 * token just granted (a refresh in progress holds the refresh lock, so refreshing again would wait
 * on itself), and a sign-out (by then the stored session may belong to the next sign-in).
 */
fun HttpRequestBuilder.withoutTokenRefresh() {
    attributes.put(AlreadyRetried, true)
}

private fun HttpRequestBuilder.bearerToken(): String? =
    headers[HttpHeaders.Authorization]?.takeIf { it.startsWith(BEARER) }?.removePrefix(BEARER)

private const val TAG = "Http"
