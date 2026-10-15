package com.tana.spa.feature.booking.domain

import com.tana.spa.shared.spa.Service
import com.tana.spa.shared.spa.Therapist

/** The treatment menu and the team, in the order the spa wants them shown. */
data class Catalog(val services: List<Service>, val therapists: List<Therapist>)

interface SpaCatalogRepository {
    /** Throws on any failure, so the caller can offer a retry. */
    suspend fun catalog(): Catalog
}
