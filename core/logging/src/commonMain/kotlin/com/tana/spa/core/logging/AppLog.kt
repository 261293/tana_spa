package com.tana.spa.core.logging

import kotlin.random.Random

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** Where log lines end up. Replace [AppLog.sink] to forward to a crash/telemetry service. */
fun interface LogSink {
    fun write(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

/**
 * App-wide logger, usable from any layer (including ones without DI such as the navigator).
 *
 * Every line carries the [sessionId] of the current app launch, and every HTTP call carries a
 * request id (see `RequestTracing` in core:network) that the server echoes into its own logs.
 * Given a user's session id from a bug report you can follow their whole journey through
 * screens, ViewModel events, network calls and the server's handling of them.
 *
 * Rules for call sites: never log passwords, tokens, OTP codes, or full emails/phones —
 * use [LogRedact]. Prefer `AppLog.i` for flow milestones, `d` for chatty detail, `w` for
 * expected-but-notable failures, `e` for bugs.
 */
object AppLog {
    /** Short random id, regenerated on every app launch. */
    val sessionId: String = randomId(SESSION_ID_LENGTH)

    /** Lines below this level are dropped before their message is even built. */
    var minLevel: LogLevel = LogLevel.INFO

    var sink: LogSink = platformLogSink()

    fun newRequestId(): String = randomId(REQUEST_ID_LENGTH)

    fun d(tag: String, message: () -> String) = log(LogLevel.DEBUG, tag, null, message)
    fun i(tag: String, message: () -> String) = log(LogLevel.INFO, tag, null, message)
    fun w(tag: String, throwable: Throwable? = null, message: () -> String) = log(LogLevel.WARN, tag, throwable, message)
    fun e(tag: String, throwable: Throwable? = null, message: () -> String) = log(LogLevel.ERROR, tag, throwable, message)

    private inline fun log(level: LogLevel, tag: String, throwable: Throwable?, message: () -> String) {
        if (level < minLevel) return
        // A broken logger must never break the feature it is describing.
        try {
            sink.write(level, tag, "[$sessionId] ${message()}", throwable)
        } catch (_: Throwable) {
        }
    }

    private fun randomId(length: Int): String {
        val chars = "0123456789abcdef"
        return buildString(length) { repeat(length) { append(chars[Random.nextInt(chars.length)]) } }
    }

    private const val SESSION_ID_LENGTH = 8
    private const val REQUEST_ID_LENGTH = 12
}

internal expect fun platformLogSink(): LogSink
