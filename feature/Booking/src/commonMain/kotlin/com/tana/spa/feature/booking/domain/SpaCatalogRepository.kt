package com.tana.spa.feature.booking.domain

import com.tana.spa.shared.spa.Service
import com.tana.spa.shared.spa.Therapist

/**
 * The treatment menu and the team, in the order the spa wants them shown. [todayEpochDay] is the
 * spa's own day, which the screen counts "today" and "tomorrow" from.
 */
data class Catalog(val services: List<Service>, val therapists: List<Therapist>, val todayEpochDay: Int)

interface SpaCatalogRepository {
    /** Throws on any failure, so the caller can offer a retry. */
    suspend fun catalog(): Catalog
}
