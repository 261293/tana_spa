package com.tana.spa.feature.feed.domain.mapper

import com.tana.spa.feature.feed.TestFixtures
import com.tana.spa.feature.feed.data.remote.dto.BannerDto
import com.tana.spa.feature.feed.domain.model.BannerLink
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeMapperTest {

    @Test
    fun maps_every_dto_to_its_domain_model() {
        assertEquals(TestFixtures.banner, TestFixtures.bannerDto.toDomain())
        assertEquals(TestFixtures.category, TestFixtures.categoryDto.toDomain())
        assertEquals(TestFixtures.service, TestFixtures.serviceDto.toDomain())
        assertEquals(TestFixtures.product, TestFixtures.productDto.toDomain())
        assertEquals(TestFixtures.appointment, TestFixtures.appointmentDto.toDomain())
    }

    @Test
    fun an_unknown_banner_link_type_is_no_link() {
        assertEquals(BannerLink.NONE, BannerDto(id = "b", title = "t", linkType = "surprise").toDomain().link)
    }
}
