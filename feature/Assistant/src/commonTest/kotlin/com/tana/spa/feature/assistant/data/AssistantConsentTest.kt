package com.tana.spa.feature.assistant.data

import com.tana.spa.core.cache.InMemoryKeyValueCache
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssistantConsentTest {
    @Test
    fun consent_is_asked_once_and_remembered_on_the_device() {
        val cache = InMemoryKeyValueCache()
        assertFalse(AssistantConsent(cache).given)

        AssistantConsent(cache).give()

        assertTrue(AssistantConsent(cache).given, "a later visit reads the same answer")
    }

    @Test
    fun consent_to_older_wording_does_not_count() {
        val cache = InMemoryKeyValueCache().apply { put("assistant.consent", "0") }
        assertFalse(AssistantConsent(cache).given)
    }
}
