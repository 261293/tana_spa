package com.tana.spa.core.logging

import kotlin.time.Clock

/** One-line format for sinks whose platform adds no timestamp/level of its own. */
internal fun formatLine(level: LogLevel, tag: String, message: String, throwable: Throwable?): String {
    val base = "${Clock.System.now()} ${level.name.padEnd(LEVEL_WIDTH)} $tag: $message"
    return if (throwable == null) base else "$base\n${throwable.stackTraceToString()}"
}

private const val LEVEL_WIDTH = 5
