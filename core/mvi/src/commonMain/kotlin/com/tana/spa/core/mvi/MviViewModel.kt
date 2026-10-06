package com.tana.spa.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base for unidirectional-data-flow screens:
 *
 *  - [S] state: one immutable snapshot the UI renders ([state])
 *  - [E] event: user/system intents going in ([onEvent])
 *  - [F] effect: one-shot outputs (snackbar, navigation) that must not replay ([effects])
 */
abstract class MviViewModel<S, E, F>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<F>(Channel.BUFFERED)
    val effects: Flow<F> = _effects.receiveAsFlow()

    /** Screen/ViewModel name used as the log tag, e.g. `LoginViewModel`. */
    protected val logTag: String = this::class.simpleName ?: "ViewModel"

    init {
        AppLog.i(logTag) { "created" }
    }

    /**
     * Entry point for the UI. Logs the event's type only (never its payload: events carry typed
     * passwords and codes), then hands it to [handleEvent].
     */
    fun onEvent(event: E) {
        AppLog.d(logTag) { "event ${event.typeName()}" }
        handleEvent(event)
    }

    protected abstract fun handleEvent(event: E)

    private fun Any?.typeName(): String = if (this == null) "null" else this::class.simpleName ?: "?"

    protected fun setState(reduce: S.() -> S) = _state.update { it.reduce() }

    protected fun sendEffect(effect: F) {
        AppLog.i(logTag) { "effect ${effect.typeName()}" }
        viewModelScope.launch { _effects.send(effect) }
    }

    override fun onCleared() {
        AppLog.i(logTag) { "cleared" }
        super.onCleared()
    }
}
