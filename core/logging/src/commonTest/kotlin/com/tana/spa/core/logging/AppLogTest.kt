package com.tana.spa.core.logging

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLogTest {
    private val lines = mutableListOf<Pair<LogLevel, String>>()
    private val originalSink = AppLog.sink
    private val originalLevel = AppLog.minLevel

    @AfterTest
    fun restore() {
        AppLog.sink = originalSink
        AppLog.minLevel = originalLevel
    }

    private fun capture() {
        AppLog.sink = LogSink { level, _, message, _ -> lines += level to message }
    }

    @Test
    fun linesCarryTheSessionId() {
        capture()
        AppLog.minLevel = LogLevel.INFO
        AppLog.i("T") { "hello" }
        assertEquals("[${AppLog.sessionId}] hello", lines.single().second)
    }

    @Test
    fun belowMinLevelIsDroppedWithoutBuildingTheMessage() {
        capture()
        AppLog.minLevel = LogLevel.WARN
        var built = false
        AppLog.d("T") {
            built = true
            "x"
        }
        AppLog.i("T") {
            built = true
            "x"
        }
        assertFalse(built)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun aThrowingSinkNeverEscapes() {
        AppLog.sink = LogSink { _, _, _, _ -> error("boom") }
        AppLog.minLevel = LogLevel.DEBUG
        AppLog.e("T") { "still fine" }
    }

    @Test
    fun redactionHidesPersonalData() {
        assertEquals("j***@example.com", LogRedact.email("jane@example.com"))
        assertEquals("***", LogRedact.email("not-an-email"))
        assertEquals("***67", LogRedact.phone("+84 901 234 567"))
    }
}
