package com.tana.spa.feature.booking.presentation

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.booking.domain.SpaCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One treatment from the server's catalogue, named by the slug the route carries. */
class ServiceDetailViewModel(private val catalog: SpaCatalogRepository) :
    MviViewModel<ServiceDetailState, ServiceDetailEvent, ServiceDetailEffect>(ServiceDetailState()) {

    override fun handleEvent(event: ServiceDetailEvent) {
        when (event) {
            is ServiceDetailEvent.Opened -> load(event.serviceId)

            ServiceDetailEvent.Retry -> load(state.value.serviceId)

            ServiceDetailEvent.Book -> {
                AppLog.i(logTag) { "book ${state.value.serviceId}" }
                sendEffect(ServiceDetailEffect.ReturnToBooking)
            }
        }
    }

    private fun load(id: String?) {
        setState { ServiceDetailState(serviceId = id, loading = true) }
        viewModelScope.launch {
            runCatchingCancellable { id?.let { slug -> catalog.catalog().services.firstOrNull { it.id == slug } } }
                .onSuccess { service ->
                    if (service == null) AppLog.w(logTag) { "no treatment with slug $id" }
                    setState { copy(loading = false, service = service, notFound = service == null) }
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
