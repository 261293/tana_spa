package com.tana.spa.core.logging

// stdout: Xcode console on iOS, the terminal on desktop, the browser devtools console on web.
internal actual fun platformLogSink(): LogSink = LogSink { level, tag, message, throwable ->
    println(formatLine(level, tag, message, throwable))
}
