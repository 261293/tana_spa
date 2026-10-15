package com.tana.spa.booking

import com.tana.spa.auth.CreateUserResult
import com.tana.spa.auth.PostgresUserRepository
import com.tana.spa.db.TestDatabase
import com.tana.spa.featureflags.FeatureFlagService
import com.tana.spa.featureflags.InMemoryFeatureFlagRepository
import com.tana.spa.notifications.NotificationService
import com.tana.spa.notifications.PostgresNotificationRepository
import com.tana.spa.notifications.push.DevPushSender
import com.tana.spa.shared.booking.BookingErrorCodes
import com.tana.spa.shared.booking.DeclineReasonDto
import com.tana.spa.shared.featureflags.ConfigurableFeatureFlags
import com.tana.spa.shared.featureflags.FeatureFlag
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs against the real schema: the rules worth checking are the ones that involve the diary —
 * what a code is worth against a treatment, who already holds a slot, and that a decision lands
 * once.
 */
class BookingServiceTest {

    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")
    private val dataSource = TestDatabase.dataSource
    private val repository = PostgresBookingRepository(dataSource)
    private val users = PostgresUserRepository(dataSource)
    private val notificationRepository = PostgresNotificationRepository(dataSource)

    /** A Wednesday well clear of today, so no other test's bookings share it. */
    private val day: LocalDate = LocalDate.now(zone).plusDays(21)
    private val now: Instant = day.minusDays(1).atTime(12, 0).atZone(zone).toInstant()

    // Notifications ship off; the guest only hears about a decision when the flag is on.
    private val flags = FeatureFlagService(
        repository = InMemoryFeatureFlagRepository(),
        users = users,
        deployDefaults = ConfigurableFeatureFlags(mapOf(FeatureFlag.NOTIFICATIONS to true)),
    )
    private val notifications = NotificationService(notificationRepository, users, DevPushSender(), flags)

    private val service = BookingService(
        repository = repository,
        notifications = notifications,
        zone = zone,
        clock = { now },
    )

    private fun newUser(): String {
        val id = UUID.randomUUID().toString().take(10)
        val phone = "+843" + (10_000_000 + (System.nanoTime() % 80_000_000L)).toString().takeLast(8)
        return assertIs<CreateUserResult.Created>(users.create("$id@example.com", "Amara", phone, "h")).user.id
    }

    private fun slot(hour: Int, minute: Int = 0): Instant = day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()

    @Test
    fun the_seeded_catalogue_is_served() = runTest {
        val services = service.services()
        assertTrue(services.any { it.slug == "warm-stone-massage" && it.minutes == 90 && it.price == 165L })
        assertTrue(service.therapists().map { it.slug }.containsAll(listOf("mai", "lena", "tomas")))
    }

    @Test
    fun a_percentage_code_only_counts_against_a_treatment_it_covers() = runTest {
        val onFacial = service.checkCode("jade20", "jade-roller-facial")
        assertNull(onFacial.refusal)
        assertEquals(24, onFacial.discount) // 20% of 120

        val onMassage = service.checkCode("JADE20", "warm-stone-massage")
        assertEquals(BookingErrorCodes.INVALID_CODE, onMassage.refusal)
        assertEquals(0, onMassage.discount)
    }

    @Test
    fun a_code_below_its_minimum_spend_is_refused() = runTest {
        // MIDWEEK15 needs $120; the body polish is $98.
        val tooCheap = service.checkCode("MIDWEEK15", "matcha-body-polish")
        assertEquals(BookingErrorCodes.INVALID_CODE, tooCheap.refusal)

        val rich = service.checkCode("MIDWEEK15", "warm-stone-massage")
        assertNull(rich.refusal)
        assertEquals(15, rich.discount)
    }

    @Test
    fun an_unknown_code_is_refused() = runTest {
        assertEquals(BookingErrorCodes.INVALID_CODE, service.checkCode("NOPE", "warm-stone-massage").refusal)
    }

    @Test
    fun a_request_discounts_the_total_and_waits_for_the_desk() = runTest {
        val booked = assertIs<BookingOutcome.Created>(
            service.create(
                userId = newUser(),
                guestName = "Amara Liu",
                guestPhone = "+14155550186",
                serviceSlug = "jade-roller-facial",
                therapistSlug = "lena",
                startAt = slot(16),
                code = "JADE20",
                note = " first visit ",
            ),
        ).booking

        assertEquals("pending", booked.status)
        assertEquals(120, booked.subtotal.toInt())
        assertEquals(24, booked.discount.toInt())
        assertEquals(96, booked.total.toInt())
        assertEquals("first visit", booked.note)
        assertTrue(booked.reference.startsWith("BK-"))
    }

    @Test
    fun a_therapist_cannot_be_in_two_rooms_at_once() = runTest {
        val first = assertIs<BookingOutcome.Created>(
            service.create(newUser(), "Mai Guest", "+14155550101", "warm-stone-massage", "mai", slot(13), null, null),
        ).booking
        assertNotNull(first)

        // 90 minutes from 1:00 PM still covers 2:30 PM.
        val clash = service.create(newUser(), "Other", "+14155550102", "deep-tissue-massage", "mai", slot(14, 30), null, null)

        assertEquals(BookingErrorCodes.SLOT_TAKEN, assertIs<BookingOutcome.Refused>(clash).code)
    }

    @Test
    fun a_slot_the_house_does_not_offer_is_refused() = runTest {
        val odd = service.create(newUser(), "Odd", "+14155550103", "matcha-body-polish", "tomas", slot(15, 15), null, null)
        assertEquals(BookingErrorCodes.SLOT_TAKEN, assertIs<BookingOutcome.Refused>(odd).code)
    }

    @Test
    fun a_time_already_gone_is_refused() = runTest {
        val past = service.create(
            userId = newUser(),
            guestName = "Late",
            guestPhone = "+14155550104",
            serviceSlug = "matcha-body-polish",
            therapistSlug = "tomas",
            startAt = now.minusSeconds(3600),
            code = null,
            note = null,
        )
        assertEquals(BookingErrorCodes.SLOT_IN_PAST, assertIs<BookingOutcome.Refused>(past).code)
    }

    @Test
    fun availability_marks_the_slots_a_therapist_already_holds() = runTest {
        service.create(newUser(), "Held", "+14155550105", "warm-stone-massage", "lena", slot(10), null, null)

        val slots = service.availability("lena", day)
        assertEquals(8, slots.size)
        assertTrue(slots.single { it.first == slot(10) }.second.not())
        // 10:00 + 90 minutes runs to 11:30, so that one is gone too.
        assertTrue(slots.single { it.first == slot(11, 30) }.second.not())
        assertTrue(slots.single { it.first == slot(13) }.second)
    }

    @Test
    fun confirming_notifies_the_guest_and_cannot_happen_twice() = runTest {
        val userId = newUser()
        val booking = assertIs<BookingOutcome.Created>(
            service.create(userId, "Amara", "+14155550106", "deep-tissue-massage", "tomas", slot(17, 30), null, null),
        ).booking

        val confirmed = assertIs<BookingOutcome.Decided>(service.confirm(booking.id, newUser(), force = false)).booking
        assertEquals("confirmed", confirmed.status)

        val stored = notificationRepository.list(userId, limit = 10, beforeEpochMicros = null)
        assertTrue(stored.any { it.title == "Booking confirmed" })

        val again = service.confirm(booking.id, newUser(), force = false)
        assertEquals(BookingErrorCodes.ALREADY_DECIDED, assertIs<BookingOutcome.Refused>(again).code)
    }

    @Test
    fun declining_records_the_reason_and_tells_the_guest() = runTest {
        val userId = newUser()
        val booking = assertIs<BookingOutcome.Created>(
            service.create(userId, "Amara", "+14155550107", "herbal-compress-ritual", "mai", slot(19), null, null),
        ).booking

        val declined = assertIs<BookingOutcome.Decided>(
            service.decline(booking.id, newUser(), DeclineReasonDto.NO_ROOM, "Today 1:00 PM with Mai Tran"),
        ).booking

        assertEquals("declined", declined.status)
        assertEquals("no_room", declined.declineReason)
        assertEquals("Today 1:00 PM with Mai Tran", declined.alternativeOffer)
        val stored = notificationRepository.list(userId, limit = 10, beforeEpochMicros = null)
        assertTrue(stored.any { it.title == "Time unavailable" })
    }

    @Test
    fun an_unknown_booking_cannot_be_decided() = runTest {
        val missing = service.confirm(UUID.randomUUID().toString(), newUser(), force = false)
        assertEquals(BookingErrorCodes.UNKNOWN_BOOKING, assertIs<BookingOutcome.Refused>(missing).code)
    }

    @Test
    fun a_published_code_is_live_and_cannot_be_published_twice() = runTest {
        val code = "TEST" + System.nanoTime().toString().takeLast(6)
        val voucher = NewVoucher(
            code = code,
            title = "15% off body treatments",
            percent = true,
            value = 15,
            minOrder = 0,
            startsAt = now.minusSeconds(60),
            endsAt = null,
            usageLimit = 100,
            eligibleCategories = listOf("body"),
        )
        assertTrue(service.createCode(voucher))
        assertTrue(service.offers().any { it.code == code })
        assertEquals(false, service.createCode(voucher))

        assertTrue(service.disableCode(code))
        assertTrue(service.offers().none { it.code == code })
    }
}
