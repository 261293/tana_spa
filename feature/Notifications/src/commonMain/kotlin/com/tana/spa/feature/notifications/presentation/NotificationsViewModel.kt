package com.tana.spa.feature.notifications.presentation

import androidx.lifecycle.viewModelScope
import com.tana.spa.core.logging.AppLog
import com.tana.spa.core.mvi.MviViewModel
import com.tana.spa.core.notifications.PushPermission
import com.tana.spa.core.notifications.PushPermissions
import com.tana.spa.core.notifications.PushRegistrar
import com.tana.spa.core.notifications.PushRegistration
import com.tana.spa.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val repository: NotificationsRepository,
    private val permissions: PushPermissions,
    private val registrar: PushRegistrar,
) : MviViewModel<NotificationsState, NotificationsEvent, NotificationsEffect>(NotificationsState()) {

    init {
        refresh()
        viewModelScope.launch {
            // Only NOT_DETERMINED is worth offering: a refusal can only be undone in the system
            // settings, and nagging there is what gets an app's notifications turned off for good.
            val canAsk = permissions.status() == PushPermission.NOT_DETERMINED
            setState { copy(canEnablePush = canAsk) }
        }
    }

    override fun handleEvent(event: NotificationsEvent) {
        when (event) {
            NotificationsEvent.Refresh -> refresh()
            NotificationsEvent.LoadMore -> loadMore()
            is NotificationsEvent.ItemClicked -> markRead(event.id)
            NotificationsEvent.MarkAllRead -> markAllRead()
            NotificationsEvent.EnablePushClicked -> enablePush()
        }
    }

    private fun refresh() {
        setState { copy(loading = true, failed = false) }
        viewModelScope.launch {
            runCatchingCancellable { repository.page() }
                .onSuccess { page ->
                    setState {
                        copy(
                            items = page.items,
                            unreadCount = page.unreadCount,
                            nextCursor = page.nextCursor,
                            loading = false,
                            failed = false,
                        )
                    }
                    AppLog.i(logTag) { "loaded ${page.items.size} notifications, ${page.unreadCount} unread" }
                }
                .onFailure {
                    setState { copy(loading = false, failed = true) }
                    sendEffect(NotificationsEffect.ShowLoadFailed)
                }
        }
    }

    private fun loadMore() {
        val cursor = state.value.nextCursor ?: return
        if (state.value.loadingMore) return
        setState { copy(loadingMore = true) }
        viewModelScope.launch {
            runCatchingCancellable { repository.page(cursor) }
                .onSuccess { page ->
                    setState {
                        // Keyset paging can overlap if something arrived meanwhile, so new ids win.
                        val merged = (items + page.items).distinctBy { it.id }
                        copy(items = merged, unreadCount = page.unreadCount, nextCursor = page.nextCursor, loadingMore = false)
                    }
                }
                .onFailure {
                    // The page already on screen is still valid, so this is not a failed screen.
                    setState { copy(loadingMore = false) }
                    sendEffect(NotificationsEffect.ShowLoadFailed)
                }
        }
    }

    private fun markRead(id: String) {
        val item = state.value.items.firstOrNull { it.id == id } ?: return
        if (item.read) return
        // Optimistic: the row greys out at once and the server's count replaces the guess.
        setState {
            copy(
                items = items.map { if (it.id == id) it.copy(read = true) else it },
                unreadCount = (unreadCount - 1).coerceAtLeast(0),
            )
        }
        viewModelScope.launch {
            runCatchingCancellable { repository.markRead(id) }
                .onSuccess { remaining -> setState { copy(unreadCount = remaining) } }
                .onFailure {
                    setState { copy(items = items.map { if (it.id == id) it.copy(read = false) else it }) }
                    sendEffect(NotificationsEffect.ShowLoadFailed)
                }
        }
    }

    private fun markAllRead() {
        if (state.value.unreadCount == 0) return
        val before = state.value.items
        setState { copy(items = items.map { it.copy(read = true) }, unreadCount = 0) }
        viewModelScope.launch {
            runCatchingCancellable { repository.markAllRead() }
                .onSuccess { remaining -> setState { copy(unreadCount = remaining) } }
                .onFailure {
                    setState { copy(items = before, unreadCount = before.count { !it.read }) }
                    sendEffect(NotificationsEffect.ShowLoadFailed)
                }
        }
    }

    private fun enablePush() {
        setState { copy(canEnablePush = false) }
        viewModelScope.launch {
            val result = registrar.requestPermissionAndRegister()
            AppLog.i(logTag) { "enable push -> $result" }
            when (result) {
                PushRegistration.Registered -> sendEffect(NotificationsEffect.ShowPushEnabled)

                // Refused, or no token on this device: either way nothing will arrive, and the
                // offer is not shown again because the system prompt only appears once.
                else -> sendEffect(NotificationsEffect.ShowPushRefused)
            }
        }
    }

    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // Ktor's wasm engine reports a failed fetch as kotlin.Error, which is not an Exception.
        AppLog.w(logTag, e) { "notification call failed (${e::class.simpleName})" }
        Result.failure(e)
    }
}
