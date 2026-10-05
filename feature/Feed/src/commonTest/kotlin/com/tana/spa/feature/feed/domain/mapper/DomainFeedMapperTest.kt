package com.tana.spa.feature.feed.domain.mapper

import com.tana.spa.feature.feed.TestFixtures
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainFeedMapperTest {
    @Test
    fun maps_dto_to_domain() {
        val result = TestFixtures.dto.toDomain()
        assertEquals("1", result.id)
        assertEquals("user_1", result.author)
        assertEquals("t", result.caption)
    }
}
