package com.tana.spa.core.logging

import android.util.Log

internal actual fun platformLogSink(): LogSink = LogSink { level, tag, message, throwable ->
    when (level) {
        LogLevel.DEBUG -> Log.d(tag, message, throwable)
        LogLevel.INFO -> Log.i(tag, message, throwable)
        LogLevel.WARN -> Log.w(tag, message, throwable)
        LogLevel.ERROR -> Log.e(tag, message, throwable)
    }
}
