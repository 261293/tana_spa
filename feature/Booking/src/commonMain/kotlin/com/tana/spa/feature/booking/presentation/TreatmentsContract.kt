package com.tana.spa.feature.booking.presentation

import com.tana.spa.shared.spa.Service
import com.tana.spa.shared.spa.ServiceCategory

/**
 * The treatment menu as a page of its own: the whole catalogue, narrowed by a search term and one
 * category. A null [category] is "every category", which the design shows as the leading chip.
 */
data class TreatmentsState(
    val services: List<Service> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val query: String = "",
    val category: ServiceCategory? = null,
) {
    val visible: List<Service>
        get() {
            val term = query.trim()
            return services.filter { service ->
                (category == null || service.category == category) &&
                    (term.isEmpty() || service.matches(term))
            }
        }

    val filtered: Boolean get() = query.isNotBlank() || category != null
}

private fun Service.matches(term: String): Boolean = listOf(name, category.label, includes).any { it.contains(term, ignoreCase = true) }

sealed interface TreatmentsEvent {
    data object Opened : TreatmentsEvent
    data object Retry : TreatmentsEvent
    data class QueryChanged(val query: String) : TreatmentsEvent
    data class CategoryChosen(val category: ServiceCategory?) : TreatmentsEvent
    data object FiltersCleared : TreatmentsEvent
    data class TreatmentChosen(val serviceId: String) : TreatmentsEvent
}

sealed interface TreatmentsEffect {
    data class OpenTreatment(val serviceId: String) : TreatmentsEffect
}
