package com.tana.spa.feature.notifications

import com.tana.spa.core.cache.InMemoryKeyValueCache
import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.notifications.NotificationBadge
import com.tana.spa.core.notifications.PushPermission
import com.tana.spa.core.notifications.PushPermissions
import com.tana.spa.core.notifications.PushRegistrar
import com.tana.spa.core.notifications.PushTokens
import com.tana.spa.core.notifications.data.NotificationRemoteApi
import com.tana.spa.feature.notifications.domain.NotificationItem
import com.tana.spa.feature.notifications.domain.NotificationPage
import com.tana.spa.feature.notifications.domain.NotificationsRepository
import com.tana.spa.feature.notifications.presentation.NotificationsEffect
import com.tana.spa.feature.notifications.presentation.NotificationsEvent
import com.tana.spa.feature.notifications.presentation.NotificationsViewModel
import com.tana.spa.shared.featureflags.ConfigurableFeatureFlags
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.notifications.DevicePlatform
import com.tana.spa.shared.notifications.DeviceRegistrationRequest
import com.tana.spa.shared.notifications.NotificationListResponse
import com.tana.spa.shared.notifications.NotificationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationsViewModelTest {

    private class FakeRepository : NotificationsRepository {
        var pages = ArrayDeque<NotificationPage>()
        var failPage = false
        var failWrite = false
        val readIds = mutableListOf<String>()
        var markAllCalls = 0

        override suspend fun page(cursor: Long?): NotificationPage {
            if (failPage) throw IllegalStateException("offline")
            return pages.removeFirstOrNull() ?: NotificationPage(emptyList(), 0, null)
        }

        override suspend fun markRead(notificationId: String): Int {
            if (failWrite) throw IllegalStateException("offline")
            readIds += notificationId
            return 0
        }

        override suspend fun markAllRead(): Int {
            markAllCalls++
            if (failWrite) throw IllegalStateException("offline")
            return 0
        }
    }

    private class FakePermissions(var state: PushPermission) : PushPermissions {
        override suspend fun status() = state

        override suspend fun request() = state
    }

    private class FakeRemote : NotificationRemoteApi {
        val registered = mutableListOf<DeviceRegistrationRequest>()

        override suspend fun list(limit: Int?, before: Long?) = NotificationListResponse(emptyList(), 0)

        override suspend fun unreadCount() = 0

        override suspend fun markRead(notificationId: String): Int? = 0

        override suspend fun markAllRead() = 0

        override suspend fun registerDevice(request: DeviceRegistrationRequest) {
            registered += request
        }

        override suspend fun unregisterDevice(token: String) = Unit
    }

    private class FakeTokens : PushTokens {
        override val platform = DevicePlatform.ANDROID
        override val locale = "vi-VN"
        override val appVersion = "1.0.0"

        override suspend fun current() = "device-token-aaaaaaaa"

        override suspend fun delete() = Unit
    }

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeRepository()
    private val permissions = FakePermissions(PushPermission.GRANTED)
    private val remote = FakeRemote()

    private fun item(id: String, read: Boolean = false, createdAt: Long = 1_000L) = NotificationItem(
        id = id,
        type = NotificationType.ORDER,
        title = "Order $id",
        body = null,
        data = emptyMap(),
        read = read,
        createdAtEpochMillis = createdAt,
    )

    private fun viewModel(): NotificationsViewModel {
        val registrar = PushRegistrar(
            remote = remote,
            cache = InMemoryKeyValueCache(),
            permissions = permissions,
            tokens = FakeTokens(),
            flags = ConfigurableFeatureFlags(mapOf(FeatureFlag.NOTIFICATIONS to true)),
            sessions = AccessTokenSource { "jwt" },
        )
        return NotificationsViewModel(repository, permissions, registrar)
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theFirstPageIsLoadedOnCreation() = runTest(dispatcher) {
        repository.pages.addLast(NotificationPage(listOf(item("a"), item("b", read = true)), unreadCount = 1, nextCursor = null))

        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("a", "b"), state.items.map { it.id })
        assertEquals(1, state.unreadCount)
        assertFalse(state.loading)
        assertFalse(state.failed)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun anEmptyCentreIsAStateNotAFailure() = runTest(dispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.items.isEmpty())
        assertFalse(viewModel.state.value.failed)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun aFailedLoadOffersARetryThatRecovers() = runTest(dispatcher) {
        repository.failPage = true
        val viewModel = viewModel()
        val effects = mutableListOf<NotificationsEffect>()

        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.failed)

        repository.failPage = false
        repository.pages.addLast(NotificationPage(listOf(item("a")), 1, null))
        viewModel.onEvent(NotificationsEvent.Refresh)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.failed)
        assertEquals(listOf("a"), viewModel.state.value.items.map { it.id })
        assertTrue(effects.isEmpty() || effects.first() == NotificationsEffect.ShowLoadFailed)
    }

    @Test
    fun loadMoreAppendsThePageAndKeepsTheCursor() = runTest(dispatcher) {
        repository.pages.addLast(NotificationPage(listOf(item("a"), item("b")), 2, nextCursor = 500L))
        repository.pages.addLast(NotificationPage(listOf(item("c")), 2, nextCursor = null))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.canLoadMore)

        viewModel.onEvent(NotificationsEvent.LoadMore)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), viewModel.state.value.items.map { it.id })
        assertNull(viewModel.state.value.nextCursor)
        assertFalse(viewModel.state.value.loadingMore)
    }

    @Test
    fun anOverlappingPageDoesNotDuplicateARow() = runTest(dispatcher) {
        repository.pages.addLast(NotificationPage(listOf(item("a"), item("b")), 2, nextCursor = 500L))
        // Something arrived between the two reads, so the server's second page repeats "b".
        repository.pages.addLast(NotificationPage(listOf(item("b"), item("c")), 2, nextCursor = null))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(NotificationsEvent.LoadMore)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun loadMoreWithoutACursorDoesNothing() = runTest(dispatcher) {
        repository.pages.addLast(NotificationPage(listOf(item("a")), 1, nextCursor = null))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(NotificationsEvent.LoadMore)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("a"), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun tappingAnUnreadRowGreysItOutImmediatelyAndRecordsIt() = runTest(dispatcher) {
        repository.pages.addLast(NotificationPage(listOf(item("a")), 1, null))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(NotificationsEvent.ItemClicked("a"))

        // Optimistic: the row reads as read before the server has answered.
        assertTrue(viewModel.state.value.items.single().read)
        assertEquals(0, viewModel.state.value.unreadCount)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("a"), repository.readIds)
    }

    @Test
    fun aRowThatIsAlreadyReadIsNotSentAgain() = runTest(dispatcher) {
        repository.pages.addLast(NotificationPage(listOf(item("a", read = true)), 0, null))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(NotificationsEvent.ItemClicked("a"))
        testScheduler.advanceUntilIdle()

        assertTrue(repository.readIds.isEmpty())
    }

    @Test
    fun aFailedReadPutsTheRowBackSoTheUserCanSeeItIsStillUnread() = runTest(dispatcher) {
        repository.pages.addLast(NotificationPage(listOf(item("a")), 1, null))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        repository.failWrite = true

        viewModel.onEvent(NotificationsEvent.ItemClicked("a"))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.items.single().read)
    }

    @Test
    fun markAllReadClearsEveryRowAndTheCount() = runTest(dispatcher) {
        repository.pages.addLast(NotificationPage(listOf(item("a"), item("b")), 2, null))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(NotificationsEvent.MarkAllRead)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.items.all { it.read })
        assertEquals(0, viewModel.state.value.unreadCount)
        assertEquals(1, repository.markAllCalls)
    }

    @Test
    fun markAllReadWithNothingUnreadDoesNotCallTheServer() = runTest(dispatcher) {
        repository.pages.addLast(NotificationPage(listOf(item("a", read = true)), 0, null))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(NotificationsEvent.MarkAllRead)
        testScheduler.advanceUntilIdle()

        assertEquals(0, repository.markAllCalls)
    }

    @Test
    fun aFailedMarkAllReadRestoresTheRowsItHadGreyedOut() = runTest(dispatcher) {
        repository.pages.addLast(NotificationPage(listOf(item("a"), item("b", read = true)), 1, null))
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        repository.failWrite = true

        viewModel.onEvent(NotificationsEvent.MarkAllRead)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(false, true), viewModel.state.value.items.map { it.read })
        assertEquals(1, viewModel.state.value.unreadCount)
    }

    @Test
    fun theEnablePushOfferOnlyAppearsWhileTheUserCanStillBeAsked() = runTest(dispatcher) {
        permissions.state = PushPermission.NOT_DETERMINED
        val askable = viewModel()
        testScheduler.advanceUntilIdle()
        assertTrue(askable.state.value.canEnablePush)

        permissions.state = PushPermission.DENIED
        val refused = viewModel()
        testScheduler.advanceUntilIdle()
        assertFalse(refused.state.value.canEnablePush, "a refusal can only be undone in the system settings")

        permissions.state = PushPermission.GRANTED
        val granted = viewModel()
        testScheduler.advanceUntilIdle()
        assertFalse(granted.state.value.canEnablePush)
    }

    @Test
    fun acceptingTheOfferRegistersTheDeviceAndSaysSo() = runTest(dispatcher) {
        permissions.state = PushPermission.NOT_DETERMINED
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        permissions.state = PushPermission.GRANTED

        viewModel.onEvent(NotificationsEvent.EnablePushClicked)
        val effect = viewModel.effects.first()

        assertEquals(NotificationsEffect.ShowPushEnabled, effect)
        assertEquals(DevicePlatform.ANDROID.wire, remote.registered.single().platform)
        assertFalse(viewModel.state.value.canEnablePush, "the system prompt is one-shot, so the offer is spent")
    }

    @Test
    fun refusingTheOfferIsReportedWithoutRegisteringAnything() = runTest(dispatcher) {
        permissions.state = PushPermission.NOT_DETERMINED
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(NotificationsEvent.EnablePushClicked)
        val effect = viewModel.effects.first()

        assertEquals(NotificationsEffect.ShowPushRefused, effect)
        assertTrue(remote.registered.isEmpty())
    }
}
