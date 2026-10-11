package com.tana.spa.core.notifications

import com.tana.spa.core.cache.InMemoryKeyValueCache
import com.tana.spa.core.network.AccessTokenSource
import com.tana.spa.core.notifications.data.NotificationApiException
import com.tana.spa.core.notifications.data.NotificationRemoteApi
import com.tana.spa.shared.auth.AuthErrorCodes
import com.tana.spa.shared.featureflags.ConfigurableFeatureFlags
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.featureflags.FeatureFlags
import com.tana.spa.shared.notifications.DevicePlatform
import com.tana.spa.shared.notifications.DeviceRegistrationRequest
import com.tana.spa.shared.notifications.NotificationListResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PushRegistrarTest {

    private class FakeRemote : NotificationRemoteApi {
        val registered = mutableListOf<DeviceRegistrationRequest>()
        val unregistered = mutableListOf<String>()
        var failWith: Exception? = null

        override suspend fun list(limit: Int?, before: Long?) = NotificationListResponse(emptyList(), 0)

        override suspend fun unreadCount() = 0

        override suspend fun markRead(notificationId: String): Int? = 0

        override suspend fun markAllRead() = 0

        override suspend fun registerDevice(request: DeviceRegistrationRequest) {
            failWith?.let { throw it }
            registered += request
        }

        override suspend fun unregisterDevice(token: String) {
            failWith?.let { throw it }
            unregistered += token
        }
    }

    private class FakePermissions(var state: PushPermission) : PushPermissions {
        var prompts = 0

        override suspend fun status() = state

        override suspend fun request(): PushPermission {
            prompts++
            return state
        }
    }

    private class FakeTokens(var token: String? = "device-token-aaaaaaaa", var failure: Exception? = null) : PushTokens {
        override val platform = DevicePlatform.ANDROID
        override val locale = "vi-VN"
        override val appVersion = "1.0.0"
        var deletions = 0

        override suspend fun current(): String? {
            failure?.let { throw it }
            return token
        }

        override suspend fun delete() {
            deletions++
        }
    }

    private val remote = FakeRemote()
    private val cache = InMemoryKeyValueCache()
    private val permissions = FakePermissions(PushPermission.GRANTED)
    private val tokens = FakeTokens()
    private val flagsOn: FeatureFlags = ConfigurableFeatureFlags(mapOf(FeatureFlag.NOTIFICATIONS to true))
    private var session: String? = "jwt-access-token"

    private fun registrar(flags: FeatureFlags = flagsOn) = PushRegistrar(
        remote = remote,
        cache = cache,
        permissions = permissions,
        tokens = tokens,
        flags = flags,
        sessions = AccessTokenSource { session },
    )

    @Test
    fun aGrantedDeviceIsRegisteredWithItsPlatformAndLocale() = runTest {
        val result = registrar().registerIfPossible()

        assertEquals(PushRegistration.Registered, result)
        val request = remote.registered.single()
        assertEquals("device-token-aaaaaaaa", request.token)
        assertEquals(DevicePlatform.ANDROID.wire, request.platform)
        assertEquals("vi-VN", request.locale)
        assertEquals("1.0.0", request.appVersion)
    }

    @Test
    fun anUnchangedTokenIsNotSentAgainOnEveryLaunch() = runTest {
        registrar().registerIfPossible()

        val second = registrar().registerIfPossible()

        assertEquals(PushRegistration.Skipped(PushRegistration.Reason.UNCHANGED), second)
        assertEquals(1, remote.registered.size)
    }

    @Test
    fun aRotatedTokenIsSentWithoutBeingForced() = runTest {
        registrar().registerIfPossible()
        tokens.token = "device-token-bbbbbbbb"

        assertEquals(PushRegistration.Registered, registrar().registerIfPossible())
        assertEquals(listOf("device-token-aaaaaaaa", "device-token-bbbbbbbb"), remote.registered.map { it.token })
    }

    @Test
    fun signingInReRegistersTheSameTokenSoTheDeviceChangesOwner() = runTest {
        registrar().registerIfPossible()

        assertEquals(PushRegistration.Registered, registrar().registerIfPossible(force = true))
        assertEquals(2, remote.registered.size)
    }

    @Test
    fun theFeatureFlagStopsEverythingBeforeATokenIsEvenAskedFor() = runTest {
        val result = registrar(flags = FeatureFlags.defaults).registerIfPossible()

        assertEquals(PushRegistration.Skipped(PushRegistration.Reason.FEATURE_OFF), result)
        assertTrue(remote.registered.isEmpty())
    }

    @Test
    fun theFlagShipsOffSoNothingRegistersUntilItIsTurnedOn() = runTest {
        assertEquals(
            PushRegistration.Skipped(PushRegistration.Reason.FEATURE_OFF),
            registrar(flags = FeatureFlags.defaults).requestPermissionAndRegister(),
        )
        assertEquals(0, permissions.prompts, "the prompt is one-shot; a disabled feature must not spend it")
    }

    @Test
    fun anAnonymousAppDoesNotCallAnAuthenticatedEndpoint() = runTest {
        session = null

        assertEquals(PushRegistration.Skipped(PushRegistration.Reason.NOT_SIGNED_IN), registrar().registerIfPossible())
        assertTrue(remote.registered.isEmpty())
    }

    @Test
    fun launchingNeverPromptsAndRegistersNothingWithoutPermission() = runTest {
        permissions.state = PushPermission.NOT_DETERMINED

        val result = registrar().registerIfPossible()

        assertEquals(PushRegistration.Skipped(PushRegistration.Reason.PERMISSION_NOT_GRANTED), result)
        assertEquals(0, permissions.prompts, "a launch must not spend the one-shot system prompt")
    }

    @Test
    fun askingExplicitlyPromptsAndThenRegisters() = runTest {
        permissions.state = PushPermission.NOT_DETERMINED
        val registrar = registrar()

        val refused = registrar.requestPermissionAndRegister()
        assertEquals(PushRegistration.Skipped(PushRegistration.Reason.PERMISSION_NOT_GRANTED), refused)
        assertEquals(1, permissions.prompts)

        permissions.state = PushPermission.GRANTED
        assertEquals(PushRegistration.Registered, registrar.requestPermissionAndRegister())
    }

    @Test
    fun aPlatformWithoutPushIsSkippedQuietly() = runTest {
        permissions.state = PushPermission.UNSUPPORTED

        assertEquals(
            PushRegistration.Skipped(PushRegistration.Reason.PERMISSION_NOT_GRANTED),
            registrar().registerIfPossible(),
        )
    }

    @Test
    fun aMissingTokenIsAStateNotAFailure() = runTest {
        tokens.token = null

        assertEquals(PushRegistration.Skipped(PushRegistration.Reason.NO_TOKEN), registrar().registerIfPossible())
    }

    @Test
    fun anUnconfiguredProviderThrowingIsCaughtRatherThanCrashingTheLaunch() = runTest {
        tokens.failure = IllegalStateException("Default FirebaseApp is not initialized")

        assertEquals(PushRegistration.Skipped(PushRegistration.Reason.NO_TOKEN), registrar().registerIfPossible())
    }

    @Test
    fun aFailedCallIsNotCachedSoTheNextLaunchTriesAgain() = runTest {
        remote.failWith = NotificationApiException(500, null)

        assertIs<PushRegistration.Failed>(registrar().registerIfPossible())

        remote.failWith = null
        assertEquals(PushRegistration.Registered, registrar().registerIfPossible())
    }

    @Test
    fun aServerThatSaysTheFeatureIsOffIsSkippedNotRetriedAsAFailure() = runTest {
        remote.failWith = NotificationApiException(503, AuthErrorCodes.FEATURE_DISABLED)

        val result = registrar().registerIfPossible()

        assertEquals(PushRegistration.Skipped(PushRegistration.Reason.FEATURE_OFF), result)
    }

    @Test
    fun signingOutDetachesTheRegisteredDeviceAndForgetsIt() = runTest {
        val registrar = registrar()
        registrar.registerIfPossible()

        registrar.unregister()

        assertEquals(listOf("device-token-aaaaaaaa"), remote.unregistered)
        // Forgotten locally, so the next sign-in registers again instead of thinking it is current.
        assertEquals(PushRegistration.Registered, registrar.registerIfPossible())
    }

    @Test
    fun signOutIsNotBlockedByAFailedUnregisterCall() = runTest {
        val registrar = registrar()
        registrar.registerIfPossible()
        remote.failWith = NotificationApiException(500, null)

        registrar.unregister()

        remote.failWith = null
        assertEquals(PushRegistration.Registered, registrar.registerIfPossible(), "the local marker was still cleared")
    }

    @Test
    fun signingOutOnADeviceThatNeverRegisteredFallsBackToTheCurrentToken() = runTest {
        registrar().unregister()

        assertEquals(listOf("device-token-aaaaaaaa"), remote.unregistered)
    }
}
