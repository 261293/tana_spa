package com.tana.spa.shared.appupdate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {

    @Test
    fun missingSegmentsAreZeroSoTwoAndThreePartVersionsMatch() {
        assertEquals(AppVersion.parseOrNull("1.0.0"), AppVersion.parseOrNull("1.0"))
        assertEquals(AppVersion.parseOrNull("1.0.0"), AppVersion.parseOrNull("1"))
    }

    @Test
    fun segmentsCompareNumericallyNotAsText() {
        val nine = requireNotNull(AppVersion.parseOrNull("1.9.0"))
        val ten = requireNotNull(AppVersion.parseOrNull("1.10.0"))
        assertTrue(ten > nine, "1.10.0 is newer than 1.9.0, which string order gets backwards")
    }

    @Test
    fun equalVersionsAreNeitherNewerNorOlder() {
        val a = requireNotNull(AppVersion.parseOrNull("2.3.4"))
        val b = requireNotNull(AppVersion.parseOrNull("2.3.4"))
        assertEquals(0, a.compareTo(b))
    }

    @Test
    fun anythingThatIsNotOneToThreeNumbersIsUnparseable() {
        listOf("", "   ", "1.2.3.4", "1.2.x", "1.0.0-beta", "-1.0.0", "v1.0.0", "1..0").forEach {
            assertNull(AppVersion.parseOrNull(it), "'$it' should not parse")
        }
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parseOrNull("  1.2.3  "))
    }

    @Test
    fun printsBackAsThreeSegments() {
        assertEquals("1.0.0", AppVersion.parseOrNull("1")?.toString())
    }
}
