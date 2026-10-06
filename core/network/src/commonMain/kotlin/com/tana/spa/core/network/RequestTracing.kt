package com.tana.spa.core.network

import com.tana.spa.core.logging.AppLog
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/** Sent on every request; the server puts it in its log context and echoes it back. */
const val REQUEST_ID_HEADER = "X-Request-Id"

/** Identifies the app launch, so all of one user's requests can be found on the server. */
const val SESSION_ID_HEADER = "X-Session-Id"

/**
 * Tags each request with a fresh request id plus the app-launch session id, and logs one line per
 * call: method, path, outcome and duration. Search the server log for the id to see what the
 * backend did with that exact call.
 *
 * Only the path is logged: never query strings, headers (Authorization) or bodies.
 */
val RequestTracing = createClientPlugin("RequestTracing") {
    on(Send) { request ->
        val requestId = AppLog.newRequestId()
        request.headers.append(REQUEST_ID_HEADER, requestId)
        request.headers.append(SESSION_ID_HEADER, AppLog.sessionId)
        val what = "${request.method.value} ${request.url.build().encodedPath} req=$requestId"
        val started = TimeSource.Monotonic.markNow()
        try {
            val call = proceed(request)
            val status = call.response.status.value
            val line = { "$what -> $status in ${started.elapsedNow().inWholeMilliseconds}ms" }
            when {
                status >= SERVER_ERROR -> AppLog.e(TAG, message = line)
                status >= CLIENT_ERROR -> AppLog.w(TAG, message = line)
                else -> AppLog.i(TAG, message = line)
            }
            call
        } catch (e: CancellationException) {
            AppLog.d(TAG) { "$what cancelled after ${started.elapsedNow().inWholeMilliseconds}ms" }
            throw e
        } catch (e: Throwable) {
            // No response at all: offline, DNS, TLS, timeout, CORS in the browser.
            AppLog.w(TAG, e) { "$what failed after ${started.elapsedNow().inWholeMilliseconds}ms: ${e::class.simpleName}" }
            throw e
        }
    }
}

private const val TAG = "Http"
private const val CLIENT_ERROR = 400
private const val SERVER_ERROR = 500
