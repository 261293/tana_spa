package com.tana.spa.feature.feed.presentation.manipulator

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.feature.feed.domain.repository.FeedRepository
import com.tana.spa.feature.feed.presentation.mapper.toUi
import com.tana.spa.shared.result.AppResult
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class FeedViewmodel(private val repository: FeedRepository) : MviViewModel<FeedState, FeedEvent, FeedEffect>(FeedState()) {

    init {
        repository.feed
            .map { feed -> feed.map { it.toUi() } }
            .onEach { items -> setState { copy(items = items) } }
            .launchIn(viewModelScope)
        refresh()
    }

    override fun onEvent(event: FeedEvent) {
        when (event) {
            FeedEvent.Refresh -> refresh()
        }
    }

    private fun refresh() {
        setState { copy(isRefreshing = true, error = null) }
        viewModelScope.launch {
            val result = repository.refresh()
            setState { copy(isRefreshing = false) }
            if (result is AppResult.Failure) {
                val message = result.error.message ?: "Could not load feed"
                if (state.value.items.isEmpty()) {
                    setState { copy(error = message) }
                } else {
                    sendEffect(FeedEffect.ShowMessage(message))
                }
            }
        }
    }
}
