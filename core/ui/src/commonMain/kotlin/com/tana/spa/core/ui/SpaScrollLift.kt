package com.tana.spa.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
class SpaScrollLift {
    private var source: State<Boolean>? by mutableStateOf(null)

    val lifted: Boolean get() = source?.value == true

    internal fun attach(scrolled: State<Boolean>) {
        source = scrolled
    }

    internal fun detach(scrolled: State<Boolean>) {
        if (source === scrolled) source = null
    }
}

val LocalSpaScrollLift = staticCompositionLocalOf<SpaScrollLift?> { null }

@Composable
fun rememberSpaScrollState(initial: Int = 0): ScrollState {
    val state = rememberScrollState(initial)
    ReportScrollLift(remember(state) { derivedStateOf { state.value > 0 } })
    return state
}

@Composable
fun rememberSpaLazyListState(): LazyListState {
    val state = rememberLazyListState()
    ReportScrollLift(remember(state) { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 } })
    return state
}

@Composable
private fun ReportScrollLift(scrolled: State<Boolean>) {
    val lift = LocalSpaScrollLift.current ?: return
    DisposableEffect(lift, scrolled) {
        lift.attach(scrolled)
        onDispose { lift.detach(scrolled) }
    }
}
