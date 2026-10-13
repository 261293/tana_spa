package com.tana.spa.feature.notifications.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tana.spa.core.ui.SpaTheme
import com.tana.spa.core.ui.components.SpaPrimaryButton
import com.tana.spa.core.ui.components.SpaSecondaryButton
import com.tana.spa.feature.notifications.domain.NotificationItem
import com.tana.spa.feature.notifications.presentation.NotificationsEffect
import com.tana.spa.feature.notifications.presentation.NotificationsEvent
import com.tana.spa.feature.notifications.presentation.NotificationsState
import com.tana.spa.feature.notifications.presentation.RelativeAge
import com.tana.spa.feature.notifications.presentation.relativeAge
import com.tana.spa.feature.notifications.resources.Res
import com.tana.spa.feature.notifications.resources.notifications_empty_body
import com.tana.spa.feature.notifications.resources.notifications_empty_title
import com.tana.spa.feature.notifications.resources.notifications_enable_action
import com.tana.spa.feature.notifications.resources.notifications_enable_body
import com.tana.spa.feature.notifications.resources.notifications_enable_title
import com.tana.spa.feature.notifications.resources.notifications_enabled_message
import com.tana.spa.feature.notifications.resources.notifications_failed_title
import com.tana.spa.feature.notifications.resources.notifications_load_failed
import com.tana.spa.feature.notifications.resources.notifications_load_more
import com.tana.spa.feature.notifications.resources.notifications_mark_all_read
import com.tana.spa.feature.notifications.resources.notifications_refused_message
import com.tana.spa.feature.notifications.resources.notifications_retry
import com.tana.spa.feature.notifications.resources.notifications_time_days
import com.tana.spa.feature.notifications.resources.notifications_time_hours
import com.tana.spa.feature.notifications.resources.notifications_time_minutes
import com.tana.spa.feature.notifications.resources.notifications_time_now
import com.tana.spa.feature.notifications.resources.notifications_time_weeks
import com.tana.spa.feature.notifications.resources.notifications_title
import com.tana.spa.feature.notifications.resources.notifications_unread
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

private val ScreenPadding = 16.dp

/** Stateless: renders [state], reports intents via [onEvent], routes one-shot [effects]. */
@Composable
fun NotificationsScreen(state: NotificationsState, effects: Flow<NotificationsEffect>, onEvent: (NotificationsEvent) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            val message = when (effect) {
                NotificationsEffect.ShowLoadFailed -> getString(Res.string.notifications_load_failed)
                NotificationsEffect.ShowPushEnabled -> getString(Res.string.notifications_enabled_message)
                NotificationsEffect.ShowPushRefused -> getString(Res.string.notifications_refused_message)
            }
            snackbar.showSnackbar(message)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading && state.items.isEmpty() -> Loading()
            state.failed && state.items.isEmpty() -> LoadFailed(onRetry = { onEvent(NotificationsEvent.Refresh) })
            else -> NotificationList(state, onEvent)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun NotificationList(state: NotificationsState, onEvent: (NotificationsEvent) -> Unit) {
    // Read once per composition: every row compares against the same instant, so two rows one
    // second apart cannot render as "now" and "1 min ago".
    val now = Clock.System.now().toEpochMilliseconds()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(state, onEvent) }

        if (state.canEnablePush) {
            item { EnablePushCard(onEnable = { onEvent(NotificationsEvent.EnablePushClicked) }) }
        }

        if (state.items.isEmpty()) {
            item { EmptyState() }
        }

        items(state.items, key = { it.id }) { item ->
            NotificationRow(item = item, nowEpochMillis = now, onClick = { onEvent(NotificationsEvent.ItemClicked(item.id)) })
        }

        if (state.nextCursor != null) {
            item {
                if (state.loadingMore) {
                    Loading(Modifier.fillMaxWidth().padding(vertical = 16.dp))
                } else {
                    SpaSecondaryButton(
                        text = stringResource(Res.string.notifications_load_more),
                        onClick = { onEvent(NotificationsEvent.LoadMore) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(state: NotificationsState, onEvent: (NotificationsEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.notifications_title),
            style = SpaTheme.textStyles.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (state.unreadCount > 0) {
            Text(
                text = stringResource(Res.string.notifications_mark_all_read),
                style = SpaTheme.textStyles.label,
                color = SpaTheme.extraColors.link,
                modifier = Modifier.clickable { onEvent(NotificationsEvent.MarkAllRead) },
            )
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationItem, nowEpochMillis: Long, onClick: () -> Unit) {
    // Colour and a dot must not be the only way to tell an unread row (WCAG 1.4.1), so the state
    // is announced as well.
    val unread = stringResource(Res.string.notifications_unread)
    Surface(
        shape = RoundedCornerShape(12.dp),
        // Unread rows carry the accent; read ones fall back to the plain surface.
        color = if (item.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { if (!item.read) stateDescription = unread },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UnreadDot(read = item.read)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = SpaTheme.textStyles.sectionLead,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                item.body?.let {
                    Text(text = it, style = SpaTheme.textStyles.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = ageLabel(item.createdAtEpochMillis, nowEpochMillis),
                    style = SpaTheme.textStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Kept in the layout once read, so the text does not shift sideways as rows are opened. */
@Composable
private fun UnreadDot(read: Boolean) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(8.dp).alpha(if (read) 0f else 1f),
        content = {},
    )
}

@Composable
private fun ageLabel(createdAtEpochMillis: Long, nowEpochMillis: Long): String =
    when (val age = relativeAge(createdAtEpochMillis, nowEpochMillis)) {
        RelativeAge.Now -> stringResource(Res.string.notifications_time_now)
        is RelativeAge.Minutes -> stringResource(Res.string.notifications_time_minutes, age.value)
        is RelativeAge.Hours -> stringResource(Res.string.notifications_time_hours, age.value)
        is RelativeAge.Days -> stringResource(Res.string.notifications_time_days, age.value)
        is RelativeAge.Weeks -> stringResource(Res.string.notifications_time_weeks, age.value)
    }

@Composable
private fun EnablePushCard(onEnable: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.notifications_enable_title),
                style = SpaTheme.textStyles.sectionLead,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.notifications_enable_body),
                style = SpaTheme.textStyles.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpaPrimaryButton(text = stringResource(Res.string.notifications_enable_action), onClick = onEnable)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.notifications_empty_title),
            style = SpaTheme.textStyles.sectionLead,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.notifications_empty_body),
            style = SpaTheme.textStyles.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.notifications_failed_title),
            style = SpaTheme.textStyles.sectionLead,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        SpaPrimaryButton(text = stringResource(Res.string.notifications_retry), onClick = onRetry)
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
