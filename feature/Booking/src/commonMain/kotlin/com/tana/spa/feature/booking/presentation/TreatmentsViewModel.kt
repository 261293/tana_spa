package com.tana.spa.feature.booking.presentation

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.booking.domain.SpaCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The whole treatment menu. Filtering is local: the catalogue is one small list, read once. */
class TreatmentsViewModel(private val catalog: SpaCatalogRepository) :
    MviViewModel<TreatmentsState, TreatmentsEvent, TreatmentsEffect>(TreatmentsState()) {

    override fun handleEvent(event: TreatmentsEvent) {
        when (event) {
            TreatmentsEvent.Opened -> if (state.value.services.isEmpty()) load()
            TreatmentsEvent.Retry -> load()
            is TreatmentsEvent.QueryChanged -> setState { copy(query = event.query) }
            is TreatmentsEvent.CategoryChosen -> setState { copy(category = event.category) }
            TreatmentsEvent.FiltersCleared -> setState { copy(query = "", category = null) }
            is TreatmentsEvent.TreatmentChosen -> sendEffect(TreatmentsEffect.OpenTreatment(event.serviceId))
        }
    }

    private fun load() {
        setState { copy(loading = true, failed = false) }
        viewModelScope.launch {
            runCatchingCancellable { catalog.catalog().services }
                .onSuccess { services ->
                    AppLog.i(logTag) { "menu: ${services.size} treatments" }
                    setState { copy(loading = false, services = services) }
                }
                .onFailure { setState { copy(loading = false, failed = true) } }
        }
    }

    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // Ktor's wasm engine reports a failed fetch as kotlin.Error, which is not an Exception.
        AppLog.w(logTag, e) { "catalogue call failed (${e::class.simpleName})" }
        Result.failure(e)
    }
}
