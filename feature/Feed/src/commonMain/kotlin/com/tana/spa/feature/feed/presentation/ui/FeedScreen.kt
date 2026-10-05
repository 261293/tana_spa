package com.tana.spa.feature.feed.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tana.spa.feature.feed.presentation.manipulator.FeedEffect
import com.tana.spa.feature.feed.presentation.manipulator.FeedEvent
import com.tana.spa.feature.feed.presentation.manipulator.FeedState
import kotlinx.coroutines.flow.Flow

/** Stateless: renders [state], reports intents via [onEvent], shows one-shot [effects]. */
@Composable
fun FeedScreen(
    state: FeedState,
    effects: Flow<FeedEffect>,
    onEvent: (FeedEvent) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is FeedEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.items.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }) { feed ->
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(feed.author, style = MaterialTheme.typography.titleMedium)
                        Text(feed.caption, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            state.isRefreshing -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            state.error != null -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Error: ${state.error}")
                Button(onClick = { onEvent(FeedEvent.Refresh) }) { Text("Retry") }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
