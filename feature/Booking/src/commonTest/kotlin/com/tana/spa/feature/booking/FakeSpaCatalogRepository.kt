package com.tana.spa.feature.booking

import com.tana.spa.feature.booking.domain.Catalog
import com.tana.spa.feature.booking.domain.SpaCatalogRepository
import com.tana.spa.shared.booking.SlotMomentDto
import com.tana.spa.shared.spa.Service
import com.tana.spa.shared.spa.ServiceCategory
import com.tana.spa.shared.spa.SpaCatalog
import com.tana.spa.shared.spa.Therapist

/** The server's slugs, as the booking migration seeds them. */
object ServerCatalog {
    val stone = Service("warm-stone-massage", "Warm Stone Massage", 90, 165, ServiceCategory.MASSAGE, "Basalt stones, scalp finish")
    val facial = Service("jade-roller-facial", "Jade Roller Facial", 60, 120, ServiceCategory.FACIAL, "Double cleanse, enzyme mask")
    val polish = Service("matcha-body-polish", "Matcha Body Polish", 50, 98, ServiceCategory.BODY, "Green tea salt scrub")

    val catalog = Catalog(
        services = listOf(stone, facial, polish),
        therapists = listOf(
            Therapist("mai", "Mai Tran", "Stone & deep tissue", SlotMomentDto(SpaCatalog.today.epochDay, 14.5)),
            Therapist("lena", "Lena Okafor", "Facials & skin", null),
        ),
        todayEpochDay = SpaCatalog.today.epochDay,
    )
}

class FakeSpaCatalogRepository(private val menu: Catalog = ServerCatalog.catalog) : SpaCatalogRepository {
    var fail = false
    var calls = 0

    override suspend fun catalog(): Catalog {
        calls++
        if (fail) throw IllegalStateException("offline")
        return menu
    }
}
