package com.tana.spa.promotions

import com.tana.spa.auth.CreateUserResult
import com.tana.spa.auth.PostgresUserRepository
import com.tana.spa.booking.NewVoucher
import com.tana.spa.booking.PostgresBookingRepository
import com.tana.spa.db.TestDatabase
import com.tana.spa.featureflags.FeatureFlagService
import com.tana.spa.featureflags.InMemoryFeatureFlagRepository
import com.tana.spa.notifications.NotificationService
import com.tana.spa.notifications.PostgresNotificationRepository
import com.tana.spa.notifications.push.DevPushSender
import com.tana.spa.shared.featureflags.ConfigurableFeatureFlags
import com.tana.spa.shared.featureflags.FeatureFlag
import com.tana.spa.shared.notifications.NotificationType
import com.tana.spa.shared.spa.SpaCatalog
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs against the real schema: what matters is that a grant is recorded before the guest is told,
 * that telling them twice is impossible, and that the copy names the date the code runs out.
 */
class PromotionServiceTest {

    private val dataSource = TestDatabase.dataSource
    private val vouchers = PostgresBookingRepository(dataSource)
    private val users = PostgresUserRepository(dataSource)
    private val notificationRepository = PostgresNotificationRepository(dataSource)

    // The seeded windows are relative to migration time, so "now" has to be migration time.
    private val now: Instant = Instant.now()

    // Notifications ship off; a promotion only reaches a guest when the flag is on.
    private val flags = FeatureFlagService(
        repository = InMemoryFeatureFlagRepository(),
        users = users,
        deployDefaults = ConfigurableFeatureFlags(mapOf(FeatureFlag.NOTIFICATIONS to true)),
    )
    private val notifications = NotificationService(notificationRepository, users, DevPushSender(), flags)

    private val service = PromotionService(
        vouchers = vouchers,
        users = users,
        notifications = notifications,
        clock = { now },
    )

    private fun newUser(): String {
        val id = UUID.randomUUID().toString().take(10)
        val phone = "+844" + (10_000_000 + (System.nanoTime() % 80_000_000L)).toString().takeLast(8)
        return assertIs<CreateUserResult.Created>(users.create("$id@example.com", "Linh", phone, "h")).user.id
    }

    private fun promotionsOf(userId: String) = notificationRepository
        .list(userId, limit = 50, beforeEpochMicros = null)
        .filter { it.type == NotificationType.PROMOTION.wire }

    @Test
    fun the_welcome_code_is_granted_and_its_copy_names_the_expiry() = runTest {
        val userId = newUser()

        assertTrue(service.grant(userId, SpaCatalog.NEW_GUEST_CODE))

        val notification = promotionsOf(userId).single()
        assertEquals("Welcome gift", notification.title)
        val body = assertNotNull(notification.body)
        assertTrue(body.contains("10% off your first booking"), body)
        assertTrue(body.contains("Use FIRSTVISIT."), body)
        assertTrue(body.contains("Valid until"), body)
        assertEquals(SpaCatalog.NEW_GUEST_CODE, notification.data["code"])
    }

    @Test
    fun a_code_the_guest_already_holds_is_not_announced_twice() = runTest {
        val userId = newUser()

        assertTrue(service.grant(userId, SpaCatalog.NEW_GUEST_CODE))
        assertFalse(service.grant(userId, SpaCatalog.NEW_GUEST_CODE))

        assertEquals(1, promotionsOf(userId).size)
    }

    @Test
    fun a_lower_case_code_is_still_recognised() = runTest {
        val userId = newUser()

        assertTrue(service.grant(userId, " firstvisit "))

        assertEquals(1, promotionsOf(userId).size)
    }

    @Test
    fun an_unknown_code_grants_nothing() = runTest {
        val userId = newUser()

        assertFalse(service.grant(userId, "NOPE"))

        assertTrue(promotionsOf(userId).isEmpty())
    }

    @Test
    fun a_code_with_no_end_date_omits_the_validity_sentence() = runTest {
        val code = "EVERGREEN" + (System.nanoTime() % 1_000)
        assertTrue(
            vouchers.insertVoucher(
                NewVoucher(
                    code = code,
                    title = "5% off, always",
                    percent = true,
                    value = 5,
                    minOrder = 0,
                    startsAt = now.minusSeconds(60),
                    endsAt = null,
                    usageLimit = null,
                    eligibleCategories = emptyList(),
                ),
            ),
        )
        val userId = newUser()

        assertTrue(service.grant(userId, code))

        val body = assertNotNull(promotionsOf(userId).single().body)
        assertEquals("5% off, always. Use $code.", body)
    }

    @Test
    fun announcing_a_code_reaches_the_guests_on_the_books() = runTest {
        val first = newUser()
        val second = newUser()

        val reached = service.announce("JADE20")

        assertTrue(reached >= 2, "announced to $reached")
        listOf(first, second).forEach { userId ->
            val notification = promotionsOf(userId).single { it.data["code"] == "JADE20" }
            assertEquals("New offer", notification.title)
            val body = assertNotNull(notification.body)
            assertTrue(body.contains("20% off any facial"), body)
            assertTrue(body.contains("Valid until"), body)
        }
    }

    @Test
    fun announcing_an_unknown_code_reaches_nobody() = runTest {
        assertEquals(0, service.announce("NOT_A_CODE"))
    }
}
